package hmda.quarterly.data.api.route

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import hmda.model.filing.lar.enums._
import hmda.quarterly.data.api.dao.repo.QuarterlyGraphRepo
import hmda.quarterly.data.api.dto.QuarterGraphData._
import hmda.quarterly.data.api.route.lib.Labels.CREDIT_SCORES
import hmda.quarterly.data.api.serde.JsonSupport
import monix.execution.CancelableFuture
import monix.execution.Scheduler.Implicits.global

object MedianCreditScoresFHAByRace extends GraphRoute(
  "For FHA loans, how have median credit scores differed by race/ethnicity?",
  "Credit Score",
  "credit-scores-fha-re"
) with JsonSupport {

  private def getMedianScore(title: String, race: String): CancelableFuture[GraphSeriesSummary] =
    QuarterlyGraphRepo.fetchMedianCreditScoreByTypeByRace(FHAInsured, race)
      .map(convertToGraph(title, _)).runToFuture

  override def route: Route = pathPrefix(endpoint) {
    path("") {
      complete(
        for {
          asian <- getMedianScore("Asian", "a")
          black <- getMedianScore("Black", "b")
          hispanic <- getMedianScore("Hispanic", "h")
          white <- getMedianScore("White", "w")
        } yield GraphSeriesInfo(
          "For FHA loans, how have median credit scores differed by race/ethnicity?",
          "In 2019, median credit scores increased for all groups and, in 2020, median credit scores declined for all groups.",
          Seq(asian, black, hispanic, white),
          yLabel = CREDIT_SCORES
        )
      )
    }
  }
}
