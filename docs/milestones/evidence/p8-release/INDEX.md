# Evidence index (p8 release)

One row per evidence file: what it covers, how it turned out, and where to read it. Generated from the evidence
files' own `work_item` and `status` fields, so it never drifts from the files themselves.

| Work item | Outcome | File |
| --- | --- | --- |
| mixin-floor-counterexample | - | [mixin-floor-counterexample.json](mixin-floor-counterexample.json) |
| negative-probes | - | [negative-probes.json](negative-probes.json) |
| packaged-safety-review | CLOSED pre-W40 safety-net review only. W40/W41 release gates remain required. | [packaged-safety-review.json](packaged-safety-review.json) |
| packaged-smoke-review-input | - | [packaged-smoke-review-input.json](packaged-smoke-review-input.json) |
| pre-w40-validation | - | [pre-w40-validation.json](pre-w40-validation.json) |
| W-100 | - | [w100-decisions.json](w100-decisions.json) |
| W-102 | done_via_runtime_proof | [w102-jei-categories.json](w102-jei-categories.json) |
| release-check（扩展档） | PASSED (10 steps) | [w103-extended-release-check.json](w103-extended-release-check.json) |
| release-check | PASSED (6 steps) | [w103-final-release-check.json](w103-final-release-check.json) |
| release-check（快速档，含升级断言） | PASSED (6 steps) | [w103-release-check-upgrades.json](w103-release-check-upgrades.json) |
| W-104 | chunk_unload_done_turbine_and_teleport_open | [w104-chunk-unload.json](w104-chunk-unload.json) |
| W-105 | - | [w105-config-screen.json](w105-config-screen.json) |
| W-106 | - | [w106-ore-chain.json](w106-ore-chain.json) |
| W-107 | - | [w107-crash-robustness.json](w107-crash-robustness.json) |
| W-108 | - | [w108-security.json](w108-security.json) |
| W-109 | - | [w109-doc-sync.json](w109-doc-sync.json) |
| W-110 | blocked_needs_fixture_code | [w110-security-denial.json](w110-security-denial.json) |
| W-111 | x3_x4_x5_fully_chained | [w111-chemical-chain.json](w111-chemical-chain.json) |
| W-112 | done_for_sorting_and_tiers | [w112-factory-throughput.json](w112-factory-throughput.json) |
| W-115 | resolved_setup_error_not_defect | [w115-chemical-ejection.json](w115-chemical-ejection.json) |
| W-116 / W-104 涡轮性能 | blocked_module_not_in_dev_client | [w116-turbine.json](w116-turbine.json) |
| W-117（收口序列） | - | [w117-closeout.json](w117-closeout.json) |
| W-117 前置 / W-103 复核 | - | [w117-smoke-stability.json](w117-smoke-stability.json) |
| W-120（进行中） | deny_and_allow_readings_repeatable | [w120-security-denial.json](w120-security-denial.json) |
| W-121 | - | [w121-crash-conservation.json](w121-crash-conservation.json) |
| W-122 | - | [w122-energy-chain.json](w122-energy-chain.json) |
| W-123 | - | [w123-save-roundtrip.json](w123-save-roundtrip.json) |
| W-124 | - | [w124-config-boundary.json](w124-config-boundary.json) |
| w40-results | - | [w40-results.json](w40-results.json) |
| w40-tests | - | [w40-tests.json](w40-tests.json) |
| w42-preparation | - | [w42-preparation.json](w42-preparation.json) |
| w59-rejection | PASSED | [w59-rejection.json](w59-rejection.json) |
| w60-dev-classloader | - | [w60-dev-classloader.json](w60-dev-classloader.json) |
| w60-failures | - | [w60-failures.json](w60-failures.json) |
| W-60 | - | [w60-results.json](w60-results.json) |
| W-60b | - | [w60b-addon-negative-probes.json](w60b-addon-negative-probes.json) |
| W-61 | - | [w61-release.json](w61-release.json) |
| W-63 | - | [w63-oracle-evaluation.json](w63-oracle-evaluation.json) |
| W-64（部分） | - | [w64-client-readings.json](w64-client-readings.json) |
| W-64 | - | [w64-verification.json](w64-verification.json) |
| W-65 | - | [w65-neoforge-save-probe.json](w65-neoforge-save-probe.json) |
| W-66 | PASSED（加速模式为主） | [w66-soak.json](w66-soak.json) |
| W-69 | PASSED | [w69-packaged-client.json](w69-packaged-client.json) |
| W-70 | PASSED (6 steps) | [w70-release-check.json](w70-release-check.json) |
| W-71 | - | [w71-release-metadata.json](w71-release-metadata.json) |
| W-72 | - | [w72-deferred-review.json](w72-deferred-review.json) |
| W-73 | - | [w73-install-doc.json](w73-install-doc.json) |
| W-74a（EMI，第一步） | - | [w74a-emi-step1.json](w74a-emi-step1.json) |
| W-74b（Jade） | - | [w74b-jade.json](w74b-jade.json) |
| W-74c（JEI） | - | [w74c-jei.json](w74c-jei.json) |
| W-74d（WTHIT） | - | [w74d-wthit.json](w74d-wthit.json) |
| W-75 补充（D3 第 3 条） | - | [w75-recipe-translation.json](w75-recipe-translation.json) |
| W-75 | - | [w75-resource-inventory.json](w75-resource-inventory.json) |
| W-76 | {'blocks': {'official': 501, 'fabric': 501, 'missing': [], 'extra': []}, 'items': {'official': 1064, 'fabric': 1066, 'missing': [], 'extra': ['neoforge:bucket', 'neoforge:default']}, 'tags': {'official': 444, 'fabric': 448, 'missing': ['neoforge:damage_type/is_environment', 'projecte:block/vein/shovel'], 'extra': ['accessories:item/back', 'accessories:item/shoes', 'neoforge:damage_type/is_magic', 'neoforge:damage_type/is_poison', 'neoforge:damage_type/is_technical', 'neoforge:damage_type/is_wither']}} | [w76-registry-ids.json](w76-registry-ids.json) |
| W-77 | 部分完成：侧配置（component_config）写入并跨端一致；库存与能量未能从控制台注入，记为残留。 | [w77-runtime-content.json](w77-runtime-content.json) |
| W-78 | - | [w78-cleanup.json](w78-cleanup.json) |
| W-80 | {'items': "[{'item': {'count': 4, 'id': 'minecraft:iron_ore'}, 'slot': 0}] —— 与官方侧一致", 'component_config.config0': '[6, 6, 6, 6, 6, 6] —— 与官方侧一致', 'verdict': '机器库存与侧配置的跨端往返通过', 'energy': 'PASS —— stored=5000 跨端一致'} | [w80-save-roundtrip.json](w80-save-roundtrip.json) |
| W-81（第一遍） | - | [w81-scale-baseline.json](w81-scale-baseline.json) |
| W-82（部分） | - | [w82-multidim-networks.json](w82-multidim-networks.json) |
| W-83（受阻记录） | - | [w83-fission-blocked.json](w83-fission-blocked.json) |
| W-84 | - | [w84-ci.json](w84-ci.json) |
| W-85 | - | [w85-licenses.json](w85-licenses.json) |
| W-86 | - | [w86-changelog.json](w86-changelog.json) |
| W-87（第一遍） | - | [w87-cleanup-pass2.json](w87-cleanup-pass2.json) |
| W-87（第二遍） | - | [w87b-docs-archive.json](w87b-docs-archive.json) |
| W-88 | - | [w88-docs-final.json](w88-docs-final.json) |
| W-89 | - | [w89-regression-reproducible.json](w89-regression-reproducible.json) |
| W-91（EMI 范围补全） | - | [w91-emi-scope.json](w91-emi-scope.json) |
| W-93 | - | [w93-integration-regression.json](w93-integration-regression.json) |
| W-94 | - | [w94-registry-deltas.json](w94-registry-deltas.json) |
| W-96 | - | [w96-release-checklist.json](w96-release-checklist.json) |
| W-97 | - | [w97-core-jar-reproducible.json](w97-core-jar-reproducible.json) |
| W-98 | - | [w98-renderer-compat.json](w98-renderer-compat.json) |
| W-99 | - | [w99-third-party-energy.json](w99-third-party-energy.json) |

74 files.
