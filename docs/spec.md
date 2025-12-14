# ContextMorph 仕様

## 概要

Scala 3スタイルのコンテキスト抽象化をKotlinに提供するK2コンパイラプラグイン。

## モジュール構成

- `runtime`: アノテーションとマーカー関数（`@Given`, `@Using`, `summon`, `given`, `useScope`）
- `compiler-plugin`: FIR + IR拡張による変換処理
- `gradle-plugin`: Gradle統合（`id("io.github.fuyuz.contextmorph")`）
- `sample`: サンプルプロジェクト

## 機能

### @Given

型に対する標準インスタンスを定義する。

```kotlin
@Given
object IntOrd : Ord<Int> {
    override fun compare(a: Int, b: Int) = a.compareTo(b)
}

@Given
val intShow: Show<Int> = object : Show<Int> {
    override fun show(value: Int) = value.toString()
}

// 派生given（他のgivenに依存）
@Given
fun <T> listOrd(@Using elementOrd: Ord<T>): Ord<List<T>> = /* ... */
```

### @Using

パラメータへの自動注入を有効にする。

```kotlin
fun <T> max(a: T, b: T, @Using ord: Ord<T>): T =
    if (ord.compare(a, b) > 0) a else b

max(1, 2)  // IntOrdが自動注入される
```

### summon\<T\>()

givenインスタンスを明示的に取得する。

```kotlin
val ord = summon<Ord<Int>>()           // IntOrdを返す
val listOrd = summon<Ord<List<Int>>>() // listOrd(IntOrd)を構築
```

### given\<T\> { }

ブロックスコープでgivenをオーバーライドする。

```kotlin
val ascending = listOf(3, 1, 2).sortedWith()  // [1, 2, 3]

given<Ord<Int>> {
    object : Ord<Int> {
        override fun compare(a: Int, b: Int) = b.compareTo(a)  // 逆順
    }
}

val descending = listOf(3, 1, 2).sortedWith()  // [3, 2, 1]
```

### context parameters統合

Kotlin 2.2+のコンテキストパラメータにも自動注入される。

```kotlin
context(ord: Ord<T>)
fun <T> maximum(a: T, b: T): T =
    if (ord.compare(a, b) > 0) a else b

maximum(1, 2)  // IntOrdが自動注入される
```

### useScope

後続のコードをラッパーラムダに注入するDSLヘルパー。

```kotlin
useScope { content ->
    wrapper {
        content()
    }
}
statement1()
statement2()
```

## 優先順位

1. ブロックスコープのgiven
2. 関数パラメータ（@Using / context）
3. クラススコープのgiven
4. ファイルスコープのgiven
5. インポートされたgiven

`@Given(priority = N)` で優先度を指定可能（値が大きいほど優先）。
