package chester.parser

import fastparse._
import NoWhitespace._
import chester.error._
import chester.syntax.concrete._
import chester.utils.StringIndex
import chester.utils.parse._

import java.lang.Character.{isDigit, isLetter}
import scala.util._

object REPL {

  sealed trait REPLResult
  case class Complete(result: Either[ParseError, ParsedExpr]) extends REPLResult
  case class UnmatchedPair(error: ParseError) extends REPLResult

  sealed trait PairCheckResult
  case object Unclosed extends PairCheckResult
  case object Closed extends PairCheckResult
  case class PairError(error: ParseError) extends PairCheckResult

  // Function to parse a single line and determine if more lines are needed
  private def parseLine(line: String, linesOffset: Int, posOffset: Int): Either[ParseError, ParsedExpr] = {
    parse(line, p => new ParserInternal("repl", linesOffset = linesOffset, posOffset = posOffset)(p).exprEntrance) match {
      case Parsed.Success(expr, _) => Right(expr)
      case f: Parsed.Failure if f.index == line.length => Left(ParseError(f.msg, Pos.Zero))
      case f: Parsed.Failure => Left(ParseError(f.msg, Pos.Zero))
    }
  }

  // Function to handle multiple lines of input and parse them as a single expression
  private def handleInput(lines: Vector[String], linesOffset: Int, posOffset: Int): Either[ParseError, ParsedExpr] = {
    val input = lines.mkString("\n")
    parse(input, p => new ParserInternal("repl", linesOffset = linesOffset, posOffset = posOffset)(p).exprEntrance) match {
      case Parsed.Success(expr, _) => Right(expr)
      case f: Parsed.Failure => Left(ParseError(f.msg, Pos.Zero))
    }
  }

  import scala.util.boundary
  import scala.util.boundary.break

  // Function to determine if the input has unclosed brackets, parentheses, or braces
  private def checkUnclosedPairs(input: String): PairCheckResult = {
    boundary {
      val stack = scala.collection.mutable.Stack[(Char, Int)]()
      val indexer = StringIndex(input)
      for ((char, index) <- input.zipWithIndex) {
        char match {
          case '(' | '[' | '{' =>
            stack.push((char, index))
          case ')' =>
            if (stack.isEmpty || stack.pop()._1 != '(') {
              val pos = indexer.charIndexToUnicodeLineAndColumn(index)
              break {
                PairError(ParseError(s"Unmatched parenthesis at position $index", Pos(index, pos.line, pos.column)))
              }
            }
          case ']' =>
            if (stack.isEmpty || stack.pop()._1 != '[') {
              val pos = indexer.charIndexToUnicodeLineAndColumn(index)
              break {
                PairError(ParseError(s"Unmatched bracket at position $index", Pos(index, pos.line, pos.column)))
              }
            }
          case '}' =>
            if (stack.isEmpty || stack.pop()._1 != '{') {
              val pos = indexer.charIndexToUnicodeLineAndColumn(index)
              break {
                PairError(ParseError(s"Unmatched brace at position $index", Pos(index, pos.line, pos.column)))
              }
            }
          case _ =>
        }
      }
      if (stack.nonEmpty) Unclosed else Closed
    }
  }

  // Function to add a line to the current input and check if it forms a complete expression
  def addLine(replLines: ReplLines, newLine: String): Either[ReplLines, REPLResult] = {
    replLines.addLine(newLine)
    val input = replLines.getPendingLines.mkString("\n")
    val linesOffset = replLines.getLinesOffset
    val posOffset = replLines.getPosOffset

    checkUnclosedPairs(input) match {
      case PairError(error) => Right(UnmatchedPair(error))
      case Unclosed => Left(replLines)
      case Closed =>
        parseLine(input, linesOffset = linesOffset, posOffset = posOffset) match {
          case Right(expr) =>
            replLines.clearPendingLines()
            Right(Complete(Right(expr)))
          case Left(_) => Left(replLines)
        }
    }
  }
}
