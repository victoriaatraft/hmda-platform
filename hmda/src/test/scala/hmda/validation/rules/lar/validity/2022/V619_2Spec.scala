package hmda.validation.rules.lar.validity._2022


import hmda.model.filing.lar.LarGenerators.larGen
import hmda.model.filing.lar.LoanApplicationRegister
import hmda.validation.rules.EditCheck
import hmda.validation.rules.lar.LarEditCheckSpec

class V619_2Spec extends LarEditCheckSpec {
  override def check: EditCheck[LoanApplicationRegister] = V619_2

  property("Action taken date must be in the current year") {
    forAll(larGen) { lar =>
      val year           = 2022
      val badActionYear  = (year - 1) * 1000 + 110
      val goodActionYear = year * 10000 + 101

      val badActionDate1 = lar.action.copy(actionTakenDate = 0)
      val badActionDate2 = lar.action.copy(actionTakenDate = badActionYear)
      lar.copy(action = badActionDate1).mustFail
      lar.copy(action = badActionDate2).mustFail
      val goodAction = lar.action.copy(actionTakenDate = goodActionYear)
      lar.copy(action = goodAction).mustPass
    }
  }
}