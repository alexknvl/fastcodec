package fastcodec

import java.io.FileOutputStream
import java.util
import java.util.UUID

import org.objectweb.asm.{ClassWriter, Label}

import scala.annotation.tailrec
import scala.annotation.unchecked.uncheckedVariance
import scala.collection.immutable.HashSet
import scala.collection.mutable

sealed trait Regex[+S] extends Product with Serializable {
  import Regex._

  def opt: Regex[S] = Alt(HashSet(Regex.Eps, this))

  def many: Regex[S] = this match {
    case Eps => Eps
    case Star(r) => this
    case r => Star(r)
  }

  def ~ [S1 >: S](that: Regex[S1]): Regex[S1] =
    seq(List(this, that))

  def | [S1 >: S](that: Regex[S1]): Regex[S1] =
    alt(List(this, that))

  def show: String = this match {
    case Regex.Eps => ""
    case Regex.Sym(s) =>
      s.toString
    case Regex.Alt(l) =>
      l.map(_.show).mkString("(", "|", ")")
    case Regex.Seq(l) =>
      l.map(_.show).mkString
    case Regex.Star(r) =>
      s"(${r.show})*"
  }

  lazy val nullable: Boolean = this match {
    case Eps => true
    case Sym(_) => false
    case Alt(l) => l.exists(_.nullable)
    case Seq(l) => l.forall(_.nullable)
    case Star(_) => true
  }

  def alphabet[S1 >: S]: Set[S1] = this match {
    case Eps => Set.empty
    case Sym(s) => Set(s)
    case Alt(l) => l.flatMap(_.alphabet)
    case Seq(l) => l.flatMap(_.alphabet).toSet
    case Star(r) => r.alphabet
  }

  def derive[S1 >: S](char: S1, contains: (S1, S1) => Boolean): Option[Regex[S1]] = this match {
    case Eps => None
    case Sym(s) if contains(s, char) => Some(Eps)
    case Sym(_) => None
    case Alt(l) =>
      l.map(_.derive(char, contains)).reduce[Option[Regex[S1]]] {
        case (None, x) => x
        case (x, None) => x
        case (Some(x), Some(y)) => Some(x | y)
      }
    case Seq(first :: rest) =>
      val restSeq = seq(rest)
      (first.derive(char, contains), first.nullable) match {
        case (None, false) => None
        case (None, true)  => restSeq.derive(char, contains)
        case (Some(first), false) =>
          Some(first ~ restSeq)
        case (Some(first), true) =>
          restSeq.derive(char, contains) match {
            case Some(value) => Some((first ~ restSeq) | value)
            case None => Some(first ~ restSeq)
          }
      }
    case Star(r) =>
      r.derive(char, contains) match {
        case Some(value) => Some(value ~ r.many)
        case None => None
      }
  }
}
object Regex {
  final case object Eps extends Regex[Nothing]
  final case class Sym[+S](s: S) extends Regex[S]
  final case class Alt[+S] private (l: HashSet[Regex[S @uncheckedVariance]]) extends Regex[S] {
    assert(l.size >= 2)
  }
  final case class Seq[+S] private (l: List[Regex[S]]) extends Regex[S] {
    assert(l.size >= 2)
  }
  final case class Star[+S] private (r: Regex[S]) extends Regex[S]

  def seq[S](r: List[Regex[S]]): Regex[S] = {
    @tailrec def go
    (s: List[List[Regex[S]]],
     result: mutable.Builder[Regex[S], List[Regex[S]]]
    ): List[Regex[S]] =
      s match {
        case Nil => result.result()
        case Nil :: xs => go(xs, result)
        case (Eps :: xs) :: xss => go(xs :: xss, result)
        case (Seq(l) :: xs) :: xss => go(l :: xs :: xss, result)
        case (x :: xs) :: xss => go(xs :: xss, result += x)
      }

    go(List(r), List.newBuilder) match {
      case Nil => Eps
      case x :: Nil => x
      case x => Seq(x)
    }
  }

  def alt[S](r: List[Regex[S]]): Regex[S] = {
    @tailrec def go
    (s: List[List[Regex[S]]],
     result: mutable.Builder[Regex[S], HashSet[Regex[S]]]
    ): HashSet[Regex[S]] =
      s match {
        case Nil => result.result()
        case Nil :: xs => go(xs, result)
        case (Alt(l) :: xs) :: xss => go(l.toList :: xs :: xss, result)
        case (x :: xs) :: xss => go(xs :: xss, result += x)
      }

    val result = go(List(r), HashSet.newBuilder)
    if (result.size == 0) sys.error("impossible")
    else if (result.size == 1) result.head
    else Alt(result)
  }

  def anyOf[S](s: SymbolSet[S]): Regex[SymbolSet[S]] =
    Sym(s)

  def char(c: Char): Regex[Char] =
    Sym(c)

  def string(s: String): Regex[Char] =
    seq(s.toCharArray.toList.map(Sym(_)))

  final case class DFA[S](states: List[DFA.State[S]])
  object DFA {
    final case class State[S](nullable: Boolean, transitions: Map[S, Int])
  }

  def buildDFA[S](r: Regex[S], contains: (S, S) => Boolean): DFA[S] = {
    val alphabet = r.alphabet

    val stateIds = mutable.HashMap.empty[Regex[S], Int]
    val states = mutable.ArrayBuffer.empty[Regex[S]]
    val transitions = mutable.ArrayBuffer.empty[mutable.HashMap[S, Int]]
    val visited = mutable.HashSet.empty[Regex[S]]

    def getId(r: Regex[S]): Int = {
      stateIds.get(r) match {
        case Some(x) => x
        case None =>
          val id = states.length
          stateIds(r) = id
          states += r
          transitions += mutable.HashMap.empty
          id
      }
    }

    val queue = new util.ArrayDeque[Regex[S]]()
    queue.add(r)
    while (queue.size() > 0) {
      val source = queue.removeFirst()

      if (!visited(source)) {
        val sourceId = getId(source)
        for (a <- alphabet) {
          source.derive(a, contains) match {
            case None => -1
            case Some(r) =>
              val target = getId(r)
              transitions(sourceId) += ((a, target))
              queue.add(r)

            //println(s"$source [$sourceId] -($a)> $r [$target]")
          }
        }
        visited(source) = true
      }
    }

    DFA[S] {
      states.indices.map { i =>
        DFA.State(
          nullable = states(i).nullable,
          transitions = transitions(i).toMap
        )
      }.toList
    }
  }

  final case class Transition(state: Int, chars: List[Char], label: Label)

  def compileCharMatcher(regex: Regex[Char]): Matcher[Char] = {
    import org.objectweb.asm.Opcodes._

    val DFA(dfa) = buildDFA[Char](regex, _ == _)

    val uuid = UUID.randomUUID().toString.replaceAllLiterally("-", "")
    val className = s"RegexMatcher_$uuid"

    val cw = new ClassWriter(0)

    cw.visit(V1_5,
      ACC_PUBLIC,     // public class
      className,      // package and name
      null, // signature (null means not generic)
      "java/lang/Object", // superclass
      Array[String]("com/alexknvl/discreteopt/Matcher")) // interfaces

    // val charArrayClass = cw.newConst(Type.getType(classOf[Array[Char]]))

    {
      val mv = cw.visitMethod(ACC_PUBLIC,
        "<init>", "()V",
        null, null)
      mv.visitCode()
      mv.visitIntInsn(ALOAD, 0)
      mv.visitMethodInsn(INVOKESPECIAL,
        "java/lang/Object", "<init>", "()V",
        false)
      mv.visitInsn(RETURN)
      mv.visitMaxs(2, 3)
      mv.visitEnd()
    }

    {
      val mv = cw.visitMethod(ACC_PUBLIC + ACC_FINAL,
        "apply", "(Ljava/lang/Object;)Z",
        null, null)
      mv.visitCode()

      val fail = new Label()
      val succeed = new Label()
      val labels = dfa.map(_ => new Label())

      // Preamble
      mv.visitIntInsn(ALOAD, 1)
      mv.visitTypeInsn(CHECKCAST, "[C")
      mv.visitIntInsn(ASTORE, 2)
      mv.visitInsn(ICONST_0)
      mv.visitIntInsn(ISTORE, 3)

      // States
      for ((state, i) <- dfa.zipWithIndex) {
        mv.visitLabel(labels(i))
        mv.visitIntInsn(ILOAD, 3)
        mv.visitIntInsn(ALOAD, 2)
        mv.visitInsn(ARRAYLENGTH)
        if (state.nullable)
          mv.visitJumpInsn(IF_ICMPGE, succeed)
        else
          mv.visitJumpInsn(IF_ICMPGE, fail)

        val transitions = state.transitions.toArray
        val tLabels = transitions.indices.map(_ => new Label())

        for (((ch, target), i) <- transitions.iterator.zipWithIndex) {
          mv.visitIntInsn(ALOAD, 2)
          mv.visitIntInsn(ILOAD, 3)
          mv.visitInsn(CALOAD)
          mv.visitIntInsn(BIPUSH, ch.toInt)
          mv.visitJumpInsn(IF_ICMPNE, tLabels(i))
          mv.visitIincInsn(3, 1)
          mv.visitJumpInsn(GOTO, labels(target))
          mv.visitLabel(tLabels(i))
        }
        mv.visitJumpInsn(GOTO, fail)
      }

      // Fail
      mv.visitLabel(fail)
      mv.visitInsn(ICONST_0)
      mv.visitInsn(IRETURN)

      // Fail
      mv.visitLabel(succeed)
      mv.visitInsn(ICONST_1)
      mv.visitInsn(IRETURN)

      mv.visitMaxs(2, 4)
      mv.visitEnd()
    }

    cw.visitEnd()

    val bytes = cw.toByteArray

    val fout = new FileOutputStream("Matcher.class")
    fout.write(bytes)
    fout.close()

    val loader = new CodeLoader
    loader.load(className, bytes).newInstance()
      .asInstanceOf[Matcher[Char]]
  }
}
