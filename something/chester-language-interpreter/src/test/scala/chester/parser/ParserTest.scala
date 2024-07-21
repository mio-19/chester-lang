package chester.parser

import munit.FunSuite
import fastparse.*
import chester.syntax.concrete._

class ParserTest extends FunSuite {

  test("parse valid identifier") {
    val result = Parser.parseExpression("testFile", "validIdentifier123")
    result match {
      case Parsed.Success(Identifier(Some(pos), name), _) =>
        assertEquals(name, "validIdentifier123")
        assertEquals(pos.fileName, "testFile")
        assertEquals(pos.range.start.line, 0)
        assertEquals(pos.range.start.column, 0)
      case _ => fail("Parsing failed")
    }
  }

  test("parse identifier with symbols") {
    val result = Parser.parseExpression("testFile", "valid-Identifier_123")
    result match {
      case Parsed.Success(Identifier(Some(pos), name), _) =>
        assertEquals(name, "valid-Identifier_123")
        assertEquals(pos.fileName, "testFile")
        assertEquals(pos.range.start.line, 0)
        assertEquals(pos.range.start.column, 0)
      case _ => fail("Parsing failed")
    }
  }

  test("parse empty input") {
    val result = Parser.parseExpression("testFile", "")
    assert(result.isInstanceOf[Parsed.Failure])
  }


  def parseAndCheck(input: String, expected: Expr): Unit = {
    val result = Parser.parseExpression("testFile", input)
    result match {
      case Parsed.Success(value, _) =>
        value match {
          case IntegerLiteral(actualValue, _) =>
            expected match {
              case IntegerLiteral(expectedValue, _) =>
                assertEquals(actualValue, expectedValue, s"Failed for input: $input")
              case _ => fail(s"Expected IntegerLiteral but got ${value.getClass} for input: $input")
            }
          case DoubleLiteral(actualValue, _) =>
            expected match {
              case DoubleLiteral(expectedValue, _) =>
                assertEquals(actualValue, expectedValue, s"Failed for input: $input")
              case _ => fail(s"Expected DoubleLiteral but got ${value.getClass} for input: $input")
            }
          case _ => fail(s"Unexpected expression type for input: $input")
        }
      case _ => fail(s"Parsing failed for input: $input")
    }
  }

  // Tests for IntegerLiteral
  test("parse valid decimal integer") {
    val input = "12345"
    val expected = IntegerLiteral(BigInt("12345"))
    parseAndCheck(input, expected)
  }

  test("parse valid hexadecimal integer") {
    val input = "0x1A3F"
    val expected = IntegerLiteral(BigInt("1A3F", 16))
    parseAndCheck(input, expected)
  }

  test("parse valid binary integer") {
    val input = "0b1101"
    val expected = IntegerLiteral(BigInt("1101", 2))
    parseAndCheck(input, expected)
  }

  test("parse signed integer") {
    val input = "-6789"
    val expected = IntegerLiteral(BigInt("-6789"))
    parseAndCheck(input, expected)
  }

  // Tests for DoubleLiteral
  test("parse valid double with exponent") {
    val input = "3.14e2"
    val expected = DoubleLiteral(BigDecimal("3.14e2"))
    parseAndCheck(input, expected)
  }

  test("parse signed double with exponent") {
    val input = "-1.23e-4"
    val expected = DoubleLiteral(BigDecimal("-1.23e-4"))
    parseAndCheck(input, expected)
  }

  test("parse double without exponent") {
    val input = "456.789"
    val expected = DoubleLiteral(BigDecimal("456.789"))
    parseAndCheck(input, expected)
  }

  // General literal tests
  test("parse integerLiteral") {
    val input = "12345"
    val expected = IntegerLiteral(BigInt("12345"))
    parseAndCheck(input, expected)
  }

  test("parse doubleLiteral") {
    val input = "1.23e4"
    val expected = DoubleLiteral(BigDecimal("1.23e4"))
    parseAndCheck(input, expected)
  }
}