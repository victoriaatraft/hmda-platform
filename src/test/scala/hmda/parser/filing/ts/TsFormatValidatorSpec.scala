package hmda.parser.filing.ts

import org.scalatest.{MustMatchers, PropSpec}
import org.scalatest.prop.PropertyChecks
import hmda.model.filing.ts.TsGenerators._
import TsFormatValidator._
import cats.data.NonEmptyList
import cats.data.Validated.{Invalid, Valid}
import hmda.parser.ParserErrorModel.IncorrectNumberOfFields
import hmda.parser.filing.ts.TsParserErrorModel._
import TsValidationUtils._

class TsFormatValidatorSpec
    extends PropSpec
    with PropertyChecks
    with MustMatchers {

  property("Transmittal Sheet must be valid") {
    forAll(tsGen) { ts =>
      val values = extractValues(ts)
      validateTs(values) mustBe Valid(ts)
    }
  }

  property("Transmittal Sheet must have the correct number of fields") {
    val values = List("a", "b", "c")
    validateTs(values) mustBe Invalid(
      NonEmptyList.of(IncorrectNumberOfFields(values.length)))
  }

  property(
    "Transmittal Sheet must report InvalidId for non numeric id field value") {
    forAll(tsGen) { ts =>
      val badId = badValue()
      val badValues = extractValues(ts).updated(0, badId)
      validateTs(badValues) mustBe Invalid(NonEmptyList.of(InvalidId))
    }
  }

  property(
    "Transmittal Sheet must report InvalidYear for non numeric year field value") {
    forAll(tsGen) { ts =>
      val badYear = badValue()
      val badValues = extractValues(ts).updated(2, badYear)
      validateTs(badValues) mustBe Invalid(NonEmptyList.of(InvalidYear))
    }
  }

  property(
    "Transmittal Sheet must report InvalidQuarter for non numeric quarter field value") {
    forAll(tsGen) { ts =>
      val badQuarter = badValue()
      val badValues = extractValues(ts).updated(3, badQuarter)
      validateTs(badValues) mustBe Invalid(NonEmptyList.of(InvalidQuarter))
    }
  }

  property(
    "Transmittal Sheet must report InvalidTotalLines for non numeric total lines field value") {
    forAll(tsGen) { ts =>
      val badTotalLines = badValue()
      val badValues = extractValues(ts).updated(12, badTotalLines)
      validateTs(badValues) mustBe Invalid(NonEmptyList.of(InvalidTotalLines))
    }
  }

  property(
    "Transmittal Sheet must report InvalidAgencyCode for non numeric agency code field value") {
    forAll(tsGen) { ts =>
      val badAgencyCode = badValue()
      val badValues = extractValues(ts).updated(11, badAgencyCode)
      validateTs(badValues) mustBe Invalid(NonEmptyList.of(InvalidAgencyCode))
    }
  }

  property("Transmittal Sheet must accumulate parsing errors") {
    forAll(tsGen) { ts =>
      val badId = badValue()
      val badYear = badValue()
      val badValues = extractValues(ts).updated(0, badId).updated(2, badYear)
      validateTs(badValues) mustBe Invalid(
        NonEmptyList.of(InvalidId, InvalidYear))
    }
  }

}
