package fastcodec

sealed trait SymbolSet[+S] {
  type Type <: S

  def contains[S1 >: S](value: S1): Boolean
  def toSet: Set[Type]
}
object SymbolSet {
  final case class Singleton[S](value: S) extends SymbolSet[S] {
    def contains[S1 >: S](value: S1): Boolean = value == this.value
    def toSet: Set[Type] = Set(value).asInstanceOf[Set[Type]]
  }
  final case class Range[S](start: S, end: S)(implicit ev: Enumerable[S]) extends SymbolSet[S] {
    def contains[S1 >: S](value: S1): Boolean = ???
    def toSet: Set[Type] =
      ev.iterator(start, end).toSet
        .asInstanceOf[Set[Type]]
  }

  def singleton[S](s: S): SymbolSet[S] = Singleton(s)
}