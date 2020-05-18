package fastcodec

trait Enumerable[S] {
  def iterator(from: S, to: S): Iterator[S]
}
