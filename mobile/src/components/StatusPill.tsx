import type { BookingStatus } from '@/api/types';

import { Pill } from './ui';

const LABELS: Record<BookingStatus, [string, 'neutral' | 'accent' | 'teal' | 'danger' | 'amber' | 'ink']> = {
  SCHEDULED: ['Scheduled', 'neutral'],
  PENDING_APPROVAL: ['Awaiting approval', 'amber'],
  REQUESTED: ['Finding cab', 'amber'],
  ASSIGNED: ['Cab assigned', 'accent'],
  ONBOARD: ['On trip', 'teal'],
  COMPLETED: ['Completed', 'teal'],
  CANCELLED: ['Cancelled', 'neutral'],
  NO_SHOW: ['No-show', 'danger'],
  REJECTED: ['Not approved', 'danger'],
};

export function StatusPill({ status }: { status: BookingStatus }) {
  const [label, tone] = LABELS[status];
  return <Pill label={label} tone={tone} />;
}
