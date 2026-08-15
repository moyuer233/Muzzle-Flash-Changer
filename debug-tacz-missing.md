# debug-tacz-missing

## Status
[RESOLVED]

## Symptom
MC 启动时提示 "tacz 未安装" (tacz not installed). User reports: "mc告诉我没有装前置：tacz".

## Hypotheses (verified)
1. **H1**: ~~`runtimeOnly` 配置未生效~~ → **H1 REJECTED**。runtimeOnly 实际上 *会* 部署。
2. **H2**: ~~tacz jar 在 libs/ 里但路径/文件名不匹配~~ → **H2 REJECTED**。文件名匹配。
3. **H3**: ~~copy task 在 runClient 之后才执行~~ → **H3 REJECTED**。顺序正确。
4. **H4**: ~~tacz jar 是预编译的，缺少 mods.toml/metadata~~ → **H4 REJECTED**。tacz 有 mods.toml。
5. **H5**: **H5 CONFIRMED root cause**: tacz jar 被部署两次（runtimeOnly + deployTaczToDev），触发 `java.lang.module.ResolutionException: Module forge reads more than one module named tacz`。

## Evidence
- runclient.txt line 15: `Exception in thread "main" java.lang.module.ResolutionException: Module forge reads more than one module named tacz`
- runclient.txt 0-13: forge 自动部署了 4 个 libs（javafmllanguage, lowcodelanguage, mclanguage, fmlcore）作为 runtime dep，因此 runtimeOnly 也确实会部署 tacz
- 用户反馈 "tacz 未安装" 实际是 deploy 阶段崩溃前游戏弹出 mods 列表，tacz 那一行被视作 "missing" → 误导信息

## Resolution
1. 删 `runtimeOnly files('libs/tacz-1.20.1.jar')`
2. 改 `deployTaczToDev` 用 `from file('libs/tacz-1.20.1.jar')` 直接复制，不再从 `configurations.runtimeClasspath`
3. 保留 `compileOnly files('libs/tacz-1.20.1.jar')` 让 mixin import tacz 类
