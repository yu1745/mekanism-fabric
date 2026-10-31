# Documentation index

Start here if you are new to the repository.

| Document | What it is | Audience |
|---|---|---|
| [`../README.md`](../README.md) | project entry point and the table below in shorter form | everyone |
| [`../CHANGELOG.md`](../CHANGELOG.md) | what the current build contains and what it does not | users |
| [`INSTALL.md`](INSTALL.md) | requirements, the four JARs, install steps, save migration, limitations | users |
| [`RELEASE_CHECKLIST.md`](RELEASE_CHECKLIST.md) | 发布前最后一遍的可执行清单（门禁、可复现、安装演练、产物校验和） | 发布执行者 |
| [`LICENSES.md`](LICENSES.md) | licences, bundled libraries, attribution | users, distributors |
| [`milestones/README.md`](milestones/README.md) | milestone status table and the release-limits list | everyone |
| [`milestones/ROADMAP.md`](milestones/ROADMAP.md) | the work plan behind the status table | contributors |
| [`milestones/M-release.md`](milestones/M-release.md) | per-work-item evidence index (W-xx → evidence JSON) | reviewers |
| [`milestones/evidence/`](milestones/evidence/) | raw evidence: gates, probes, comparisons | reviewers |
| [`Development.md`](Development.md) | local build, test task tiers, verify commands | contributors |
| [`FABRIC_SMOKE.md`](FABRIC_SMOKE.md) | what each smoke script proves and how to run it | contributors |
| [`FABRIC_PORT.md`](FABRIC_PORT.md) | port scope and status narrative | contributors |
| [`PORT_MATRIX.md`](PORT_MATRIX.md) | historical platform dependency inventory up to Task049 | maintainers |
| [`API_DELTAS.md`](API_DELTAS.md) | every intentional difference from the NeoForge behaviour | maintainers |
| [`PORT_COMMIT_REVIEW_PROMPT.md`](PORT_COMMIT_REVIEW_PROMPT.md) | the review prompt used for every port commit | maintainers |
| [`POLICY_HISTORY.md`](POLICY_HISTORY.md) | how the working rules evolved | maintainers |
| `FABRIC_{CHEMICAL,CONFIG,DATA_MAPS,ENERGY,FLUID,HEAT,HOLDERS,ITEM,NETWORK,RECIPES,REGISTRY,TEXT}.md` | topic notes: the platform facts and boundaries for one subsystem each | maintainers |
| [`archive/`](archive/) | closed task narratives and early-port notes (Task050–052 and earlier, incl. `MACHINE_SPINE`, `MACHINE_OWNER_ADMISSION`, `MODEL_IMPLEMENTATION_COMPARISONS`); history only, not current status | nobody, unless tracing a decision |

## Conventions

- **Current status lives in `milestones/README.md`**, not in the topic notes or the archive. Those record why
  something is the way it is.
- Every claim in a milestone document links to an evidence file under `milestones/evidence/`.
- Documents in `archive/` are frozen: they are never rewritten, and a stale link inside them is not a bug.
