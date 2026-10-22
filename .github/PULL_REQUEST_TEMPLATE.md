## Scope

Related issue and contract item:

Smallest behavior implemented or corrected:

What remains unsupported:

## Implementation and provenance

Exact source/target APIs, mapping rules and implemented adapter bindings:

Behavior differences, side/thread/lifecycle requirements:

Origin and license of contributed code, fixtures and assets:

## Validation

Commands run serially, exact versions and artifact hashes:

| Evidence | Result and scope |
| --- | --- |
| Build and unit tests | |
| Bytecode and offline linkage | |
| Original/converted behavior comparison | |
| Native client/server | |
| Multiplayer | |

State NOT_RUN, skipped tests, failures and missing inputs explicitly. Test counts
and resolved symbols are not supported-mod counts. Do not inherit an old game
test across changed artifact hashes.

## Checklist

- [ ] I preserved existing public profiles, adapter identities and schemas, or documented an explicit reviewed migration.
- [ ] Analysis/conversion do not execute original mod classes or rewrite Minecraft/Forge/Java classes.
- [ ] Unsupported behavior fails without publishing a partial result.
- [ ] Relevant positive, negative and boundary tests were added or updated.
- [ ] Documentation states scope, differences and remaining gates.
- [ ] I have the rights to contribute this material under the project terms and preserved third-party notices.
- [ ] No credentials, private mods/logs, game assets or full local mapping/API bundles are included.
- [ ] Any game tests used explicitly authorized disposable installations; build success is not presented as gameplay proof.
