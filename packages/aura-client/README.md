# @aura-flags/client

Official TypeScript and JavaScript client SDK for **Aura** — the high-performance distributed feature flag and remote configuration platform.

## Features

- ⚡ **Sub-15ms Real-Time Push:** Uses Server-Sent Events (SSE) with automatic fallback to HTTP polling.
- 🎯 **Targeting & Canary Rollouts:** Progressive percentage exposure deterministically hashed via **MurmurHash3** (`userId:flagKey`).
- 👥 **Context Segmentation:** Support for user attributes, email domain matching, and custom rules.
- 🛡️ **Zero-Flicker & Resilient:** Automatic reconnects, offline fallbacks, and local default state.

## Installation

```bash
npm install @aura-flags/client
# or
pnpm add @aura-flags/client
# or
yarn add @aura-flags/client
```

## Quick Start

```typescript
import { AuraClient } from '@aura-flags/client';

const aura = new AuraClient({
  apiKey: 'aura_pk_live_xxxxxxxxxxxxxxxxxxxxxxxx',
  environment: 'production',
  context: {
    userId: 'usr_12345',
    email: 'alex@company.com',
    attributes: { tier: 'pro', betaUser: true }
  }
});

// Evaluate flag
if (aura.isEnabled('fast-checkout-v2')) {
  console.log('Fast checkout is enabled!');
}

// Subscribe to real-time flag changes pushed from Aura Dashboard
aura.onUpdate(({ flags, changedFlags }) => {
  console.log('Flags updated in real-time:', flags);
});
```
