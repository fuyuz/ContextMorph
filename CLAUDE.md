# ContextMorph 開発ガイド

## プロジェクト構成

```
contextmorph/
├── runtime/           # ランタイムライブラリ (given/using/summon, useScope)
├── compiler-plugin/   # Kotlin K2 コンパイラプラグイン (FIR + IR)
├── gradle-plugin/     # Gradle プラグイン (composite build)
└── sample/            # サンプルプロジェクト
```

## サンプル実行

```bash
./gradlew :sample:jvmRun
```

Gradle composite buildを使用しているため、`publishToMavenLocal`なしでローカルのgradle-pluginが自動的に使用される。

## ビルドコマンド

```bash
./gradlew build                    # 全体ビルド
./gradlew :sample:jvmRun           # サンプル実行
./gradlew publishToMavenLocal      # Maven Localに公開（外部利用向け）
```

## トラブルシューティング

GradleがJavaランタイムを見つけられない場合、`JAVA_HOME`をJDK 21以上に設定して再実行。
