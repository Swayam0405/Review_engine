# Review Engine frontend

The frontend is a Next.js App Router application using TypeScript and SCSS
Modules. It communicates with the Spring Boot backend through HTTP APIs; it
does not connect directly to PostgreSQL.

## Development

```sh
npm install
npm run dev
```

Open [http://localhost:3000](http://localhost:3000) in a browser.

## Validation

```sh
npm run typecheck
npm run lint
npm run build
```

## Source layout

- `src/app/` contains routes, layouts, and route-level styles.
- `src/features/` owns feature-specific UI, hooks, services, schemas, and types.
- `src/components/` contains shared UI and layout components.
- `src/lib/api/` and `src/lib/auth/` contain shared API and authentication
  infrastructure.
- `src/providers/`, `src/hooks/`, `src/styles/`, and `src/types/` contain
  application-wide providers, hooks, design styles, and shared types.
