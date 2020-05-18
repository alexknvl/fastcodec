package fastcodec

trait Matcher[S] {
  def apply(chunk: Array[S]): Boolean
}
