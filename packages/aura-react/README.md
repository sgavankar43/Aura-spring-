# @aura-flags/react

Official React hooks and components for **Aura** — with real-time SSE updates, zero-re-render optimization, and declarative gates.

## Installation

```bash
npm install @aura-flags/react @aura-flags/client
```

## Quick Start

### 1. Wrap your application with `AuraProvider`

```tsx
import React from 'react';
import ReactDOM from 'react-dom/client';
import { AuraProvider } from '@aura-flags/react';
import App from './App';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <AuraProvider
      options={{
        apiKey: 'aura_pk_live_xxxxxxxxxxxxxxxxxxxxxxxx',
        environment: 'production',
        context: {
          userId: 'usr_987',
          email: 'user@company.com'
        }
      }}
    >
      <App />
    </AuraProvider>
  </React.StrictMode>
);
```

### 2. Use Hooks in Components

```tsx
import { useFeatureFlag, FeatureGate } from '@aura-flags/react';

export function CheckoutButton() {
  const isFastCheckout = useFeatureFlag('fast-checkout-v2', false);

  return isFastCheckout ? (
    <button className="btn-instant">⚡ 1-Click Buy</button>
  ) : (
    <button className="btn-standard">Standard Checkout</button>
  );
}

// Or declaratively via <FeatureGate>
export function BetaPortal() {
  return (
    <FeatureGate flag="beta-portal" fallback={<div>Coming Soon</div>}>
      <SecretBetaFeatures />
    </FeatureGate>
  );
}
```
