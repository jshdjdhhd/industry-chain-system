# Frontend QA Notes

Date: 2026-06-25

## Positioning

This frontend is an integration prototype. It provides the application shell, route structure, page layout, API wrappers, error display, and temporary mock data while backend, database, and AI modules are still being implemented by the assigned members.

The reference images are product-direction material. They should guide layout density and interaction style, but they are not a requirement to fake backend data or AI results in the frontend.

## Current Data Policy

- The UI shows a "front-end mock data" badge whenever `VITE_API_MOCK` is enabled or unset.
- Dashboard totals are calculated from the current mock dataset.
- Mock API behavior exists only to keep pages runnable before integration.
- Backend service rules remain the final authority after integration.

## Ownership Boundaries

- Member B: enterprise add/query/detail/inbound/outbound.
- Member C: tags, enterprise tag binding, import template, data cleaning.
- Member D: industry chain tree, node path, node statistics, node enterprise list.
- Member E: enterprise-node linking, AI/rule recommendation, accept/reject/manual confirmation.
- Member A: common response, exception handling, operation log, integration environment, demo data coordination.

## Verification

- `npm.cmd run format:check`
- `npm.cmd test`
- `npm.cmd run build`

This file should be updated after backend integration, not treated as final product acceptance.
