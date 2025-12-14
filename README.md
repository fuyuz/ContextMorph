# ContextMorph

> **注意**: これは個人的な技術検証を目的とした試作プラグインです。本番環境での使用は推奨しません。
>
> IDE（IntelliJ IDEA等）ではFIR拡張が正しく認識されず、エラーとして表示される場合があります。コンパイル・実行は正常に動作します。

Scala 3スタイルのコンテキスト抽象化（`given`/`using`/`summon`）をKotlinに提供するK2コンパイラプラグイン。

## 機能

- **@Given**: 型に対する標準インスタンスを定義
- **@Using**: パラメータへの自動注入
- **summon\<T\>()**: インスタンスの明示的な取得
- **given\<T\> { }**: ブロックスコープでのオーバーライド
- **context parameters**: Kotlin 2.2+ のコンテキストパラメータとの統合
- **useScope**: 後続のコードをラッパーに注入するDSLヘルパー

## 要件

- Kotlin 2.2.0+
- Gradle 8.x

## 使用例

### given/using/summon

```kotlin
import io.github.fuyuz.contextmorph.Given
import io.github.fuyuz.contextmorph.Using
import io.github.fuyuz.contextmorph.summon

interface Ord<T> {
    fun compare(a: T, b: T): Int
}

@Given
object IntOrd : Ord<Int> {
    override fun compare(a: Int, b: Int) = a.compareTo(b)
}

fun <T> max(a: T, b: T, @Using ord: Ord<T>): T =
    if (ord.compare(a, b) > 0) a else b

fun demo() {
    println(max(1, 2))                 // IntOrdが自動注入
    val ord = summon<Ord<Int>>()       // IntOrdを取得
    println(ord.compare(1, 2))
}
```

### useScope

```kotlin
import io.github.fuyuz.contextmorph.useScope

useScope { content ->
    wrapper {
        content()
    }
}
statement1()
statement2()
```

## サンプル実行

```bash
./gradlew :sample:jvmRun
```

## ライセンス

MIT License
