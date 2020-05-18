package fastcodec

import java.security.SecureClassLoader

final class CodeLoader extends SecureClassLoader {
  def load(name: String, data: Array[Byte]): Class[_] = {
    defineClass(name, data, 0, data.length)
  }
}