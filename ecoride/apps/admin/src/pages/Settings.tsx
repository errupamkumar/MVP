import type { SettingDto } from '@ecoride/core';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';

import { Button, Card, ErrorNote, Modal, PageHeader, Spinner } from '@/components/ui';
import { api } from '@/lib/api';
import { useAuth } from '@/lib/auth';

export default function Settings() {
  const { data, error } = useQuery({ queryKey: ['settings'], queryFn: api.settings });
  const [confirmReset, setConfirmReset] = useState(false);
  if (error && !data) return <ErrorNote error={error} />;
  if (!data) return <Spinner />;
  const groups = data.reduce<Record<string, SettingDto[]>>((acc, s) => {
    (acc[s.category] ??= []).push(s);
    return acc;
  }, {});
  return (
    <div>
      <PageHeader title="Settings" subtitle="Business rules applied at once, without a release. Every change is written to the audit log." actions={<Button variant="danger" onClick={() => setConfirmReset(true)}>Reset demo data</Button>} />
      <div className="grid gap-6 xl:grid-cols-2">
        {Object.entries(groups).map(([category, items]) => (
          <Card key={category} title={category} pad={false}>
            <ul className="divide-y divide-line">
              {items.map((s) => (
                <SettingRow key={s.key} setting={s} />
              ))}
            </ul>
          </Card>
        ))}
      </div>
      <ResetDialog open={confirmReset} onClose={() => setConfirmReset(false)} />
    </div>
  );
}

function SettingRow({ setting }: { setting: SettingDto }) {
  const queryClient = useQueryClient();
  const [value, setValue] = useState(setting.value);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [saved, setSaved] = useState(false);
  useEffect(() => setValue(setting.value), [setting.value]);
  const save = async (v = value) => {
    setBusy(true);
    setError(null);
    try {
      await api.updateSetting(setting.key, v);
      await queryClient.invalidateQueries({ queryKey: ['settings'] });
      setSaved(true);
      setTimeout(() => setSaved(false), 2000);
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };
  return (
    <li className="flex flex-wrap items-start justify-between gap-4 px-5 py-3.5">
      <div className="min-w-[220px] flex-1">
        <div className="text-sm font-semibold text-ink">{setting.label}</div>
        <div className="mt-0.5 text-xs text-ink-3">{setting.description}</div>
        {setting.value !== setting.defaultValue ? <div className="mt-1 text-xs text-ink-4">Default {setting.defaultValue} {setting.unit}</div> : null}
        {error ? <div className="mt-2"><ErrorNote error={error} /></div> : null}
      </div>
      <div className="flex items-center gap-2">
        {setting.type === 'BOOL' ? (
          <button
            role="switch"
            aria-checked={value === 'true'}
            onClick={() => void save(value === 'true' ? 'false' : 'true')}
            disabled={busy}
            className={`relative h-6 w-11 rounded-full transition ${value === 'true' ? 'bg-brand' : 'bg-line'}`}
          >
            <span className={`absolute top-0.5 h-5 w-5 rounded-full bg-white shadow transition ${value === 'true' ? 'left-[22px]' : 'left-0.5'}`} />
          </button>
        ) : (
          <>
            <input
              type="number"
              value={value}
              min={setting.min}
              max={setting.max}
              step={setting.type === 'DECIMAL' ? '0.05' : '1'}
              onChange={(e) => setValue(e.target.value)}
              className="h-9 w-24 rounded-lg border border-line px-2 text-right text-sm outline-none focus:border-ink"
            />
            <span className="w-10 text-xs text-ink-3">{setting.unit}</span>
            <Button size="sm" variant="primary" disabled={value === setting.value} loading={busy} onClick={() => void save()}>
              Save
            </Button>
          </>
        )}
        {saved ? <span className="text-xs font-semibold text-brand">Saved</span> : null}
      </div>
    </li>
  );
}

function ResetDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const { signOut } = useAuth();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  return (
    <Modal
      open={open}
      title="Reset the demo?"
      onClose={onClose}
      footer={
        <>
          <Button onClick={onClose}>Keep data</Button>
          <Button variant="danger" loading={busy} onClick={async () => {
            setBusy(true);
            setError(null);
            try {
              await api.resetDemo();
              queryClient.clear();
              onClose();
              signOut('Demo data was reset. Sign in again.');
            } catch (e) {
              setError(e);
            } finally {
              setBusy(false);
            }
          }}>
            Reset everything
          </Button>
        </>
      }
    >
      <p className="text-sm text-ink-2">
        Rebuilds all accounts, fares, settings and 30 days of ride history. Rides in progress and accounts created during the demo are removed. Signed-in apps keep working with the demo accounts.
      </p>
      <div className="mt-3"><ErrorNote error={error} /></div>
    </Modal>
  );
}
