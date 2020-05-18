package fastcodec

import java.lang.reflect.Modifier

import org.scalacheck.Gen
import org.scalatest.PropSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import zio.Chunk

import scala.collection.mutable

class RegexCompilerSpec extends PropSpec with ScalaCheckDrivenPropertyChecks {
  import fastcodec.Regex._

  def genPositive[S, C](r: Regex[S], f: S => Set[C]): Gen[Chunk[C]] = {
    r match {
      case Eps =>
        Gen.const(Chunk.empty)
      case Sym(s) =>
        Gen.oneOf(f(s).toList).map(Chunk.single)
      case Alt(l) =>
        Gen.oneOf(l.toList.map(genPositive[S, C](_, f))).flatMap(x => x)
      case Seq(l) =>
        Gen.sequence[List[Chunk[C]], Chunk[C]](l.map(genPositive[S, C](_, f)))
          .map(_.foldLeft(Chunk.empty : Chunk[C])(_ ++ _))
      case Star(r) =>
        val r0 = genPositive(r, f)
        for {
          cnt <- Gen.sized(sz => Gen.choose(0, sz))
          res <- Gen.containerOfN[List, Chunk[C]](cnt, r0)
        } yield res.foldLeft(Chunk.empty : Chunk[C])(_ ++ _)
    }
  }

  def genNegative(r: Regex[Char], ch: Char): Gen[Chunk[Char]] = {
    def alphabet(r: Regex[Char]): Set[Char] = r match {
      case Eps => Set.empty
      case Sym(s) => Set(s)
      case Alt(l) => l.flatMap(alphabet)
      case Seq(l) => l.flatMap(alphabet).toSet
      case Star(r) => alphabet(r)
    }

    val a = alphabet(r)
    assert(!a.contains(ch))

    for {
      pos <- genPositive[Char, Char](r, x => Set(x))
      neg <- if (pos.length > 0) {
        Gen.chooseNum(0, pos.length - 1).map { i =>
          pos.take(i) ++ Chunk.single(ch) ++ pos.drop(i + 1)
        }
      } else {
        Gen.const(Chunk.single(ch))
      }
    } yield neg
  }

  def genRegexSized(alphabet: String, sz: Int): Gen[Regex[Char]] =
    Gen.oneOf(List(
      Gen.containerOfN[Array, Char](Math.log(sz).toInt max 1, Gen.oneOf(alphabet)).map(s => Regex.string(s.mkString)),
    ) ++ (
      if (sz > 2) List(
        Gen.containerOfN[List, Regex[Char]](2 max (sz / 2), genRegexSized(alphabet, sz / 2))
          .map(l => Regex.seq(l)),
        Gen.containerOfN[List, Regex[Char]](2 max Math.log(sz).toInt, genRegexSized(alphabet, sz - 1))
          .map(l => Regex.alt(l)),
        genRegexSized(alphabet, sz / 2).map(_.many)
      ) else Nil
    )).flatMap(x => x)

  property("test") {
    import Regex._

    forAll(genRegexSized("0123", 10)) { regex =>
      println(regex.show)
      val p = Regex.compileCharMatcher(regex)

      var seen = mutable.HashSet.empty[Chunk[Char]]
      forAll(genPositive[Char, Char](regex, x => Set(x))) { v =>
        if (!seen(v)) {
          seen += v
          println("+ " + v.mkString(""))
          assert(p.apply(v.toArray[Char]))
        }
      }

      seen = mutable.HashSet.empty[Chunk[Char]]
      forAll(genNegative(regex, '5')) { v =>
        if (!seen(v)) {
          seen += v
          println("- " + v.mkString(""))
          assert(!p.apply(v.toArray[Char]))
        }
      }
    }

    val bin = char('0') | char('1')
    val binary = bin ~ bin.many

//    forAll(gen(binary)) { v =>
//      println(v.mkString(""))
//    }
//
//    println(binary.show)

    println(Regex.buildDFA[Char](binary, _ == _))

    val p = Regex.compileCharMatcher(binary)
    println(p.apply("0".toCharArray))
  }

  property("dump methods") {
    classOf[Matcher[_]].getDeclaredMethods
      .filter(m => !Modifier.isStatic(m.getModifiers))
      .foreach { m =>
        println(m)
      }
  }
}
