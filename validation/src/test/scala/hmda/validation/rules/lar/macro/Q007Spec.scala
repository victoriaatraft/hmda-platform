package hmda.validation.rules.lar.`macro`

import akka.stream.scaladsl.Source
import com.typesafe.config.ConfigFactory
import hmda.model.fi.lar.LoanApplicationRegister
import hmda.validation.rules.AggregateEditCheck
import hmda.validation.rules.lar.`macro`.MacroEditTypes.LoanApplicationRegisterSource
import org.scalacheck.Gen

class Q007Spec extends MacroSpec {

  val config = ConfigFactory.load()
  val multiplier = config.getDouble("hmda.validation.macro.Q007.numOfLarsMultiplier") * 100

  val multiplierGen: Gen[Int] = Gen.choose(1, multiplier.toInt)

  property("be valid if not accepted <= 0.15 * total") {
    larSource.mustPass
  }

  property("be invalid if withdrawn > 0.15 * total") {
    forAll(multiplierGen) { multiplier =>
      val badLar = lars.head.copy(actionTakenType = 2)
      val badLars = Array.fill(multiplier)(badLar)
      val goodLars = Array.fill(100 - multiplier)(lars.head)
      val newLars = badLars ++ goodLars
      val newLarSource = Source.fromIterator(() => newLars.toIterator)
      newLarSource.mustFail
    }
  }

  override def check: AggregateEditCheck[LoanApplicationRegisterSource, LoanApplicationRegister] = Q007
}
