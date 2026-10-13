/** A stand-in for the module `cloudflare:workers` in the tests on Node (vitest.config.ts). */
export class WorkerEntrypoint<Env = unknown> {
  constructor(protected readonly ctx: unknown, protected readonly env: Env) {}
}
