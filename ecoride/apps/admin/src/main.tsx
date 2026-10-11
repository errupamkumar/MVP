import './index.css';

import type { ApiError } from '@ecoride/core';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';

import { Layout } from '@/components/Layout';
import { AuthProvider, useAuth } from '@/lib/auth';
import Audit from '@/pages/Audit';
import Dashboard from '@/pages/Dashboard';
import DriverDetail from '@/pages/DriverDetail';
import Drivers from '@/pages/Drivers';
import Finance from '@/pages/Finance';
import Live from '@/pages/Live';
import Login from '@/pages/Login';
import Pricing from '@/pages/Pricing';
import Promos from '@/pages/Promos';
import RideDetail from '@/pages/RideDetail';
import RiderDetail from '@/pages/RiderDetail';
import Riders from '@/pages/Riders';
import Rides from '@/pages/Rides';
import Safety from '@/pages/Safety';
import Settings from '@/pages/Settings';
import Surge from '@/pages/Surge';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 5000,
      retry: (count, error) => {
        const e = error as unknown as ApiError;
        return count < 2 && (e?.status === 0 || (e?.status ?? 0) >= 500);
      },
    },
  },
});

function Protected() {
  const { session } = useAuth();
  return session ? <Layout /> : <Navigate to="/login" replace />;
}

function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route element={<Protected />}>
        <Route index element={<Dashboard />} />
        <Route path="live" element={<Live />} />
        <Route path="rides" element={<Rides />} />
        <Route path="rides/:id" element={<RideDetail />} />
        <Route path="drivers" element={<Drivers />} />
        <Route path="drivers/:id" element={<DriverDetail />} />
        <Route path="riders" element={<Riders />} />
        <Route path="riders/:id" element={<RiderDetail />} />
        <Route path="pricing" element={<Pricing />} />
        <Route path="surge" element={<Surge />} />
        <Route path="promos" element={<Promos />} />
        <Route path="finance" element={<Finance />} />
        <Route path="safety" element={<Safety />} />
        <Route path="settings" element={<Settings />} />
        <Route path="audit" element={<Audit />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <App />
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
);
