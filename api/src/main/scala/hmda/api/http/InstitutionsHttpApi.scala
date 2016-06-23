package hmda.api.http

import akka.actor.{ ActorRef, ActorSelection, ActorSystem }
import akka.event.LoggingAdapter
import akka.stream.ActorMaterializer
import akka.pattern.ask
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.{ ContentTypes, HttpEntity, HttpResponse, StatusCodes }
import akka.util.Timeout
import hmda.api.model._
import hmda.api.persistence.CommonMessages._
import hmda.api.persistence.{ FilingPersistence, SubmissionPersistence }
import hmda.api.persistence.FilingPersistence.GetFilingByPeriod
import hmda.api.persistence.InstitutionPersistence.GetInstitutionById
import hmda.api.persistence.SubmissionPersistence.{ CreateSubmission, GetLatestSubmission }
import hmda.api.protocol.processing.{ FilingProtocol, InstitutionProtocol }
import hmda.model.fi.{ Filing, Institution, Submission }
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }
import spray.json._

trait InstitutionsHttpApi extends InstitutionProtocol {

  implicit val system: ActorSystem
  implicit val materializer: ActorMaterializer
  val log: LoggingAdapter

  implicit val timeout: Timeout

  val institutionsPath =
    path("institutions") {
      val institutionsActor = system.actorSelection("/user/institutions")
      get {
        val fInstitutions = (institutionsActor ? GetState).mapTo[Set[Institution]]
        onComplete(fInstitutions) {
          case Success(institutions) =>
            complete(ToResponseMarshallable(Institutions(institutions)))
          case Failure(error) =>
            log.error(error.getLocalizedMessage)
            complete(HttpResponse(StatusCodes.InternalServerError))
        }
      }
    }

  val institutionByIdPath =
    path("institutions" / Segment) { fid =>
      extractExecutionContext { executor =>
        val institutionsActor = system.actorSelection("/user/institutions")
        val filingsActor = system.actorOf(FilingPersistence.props(fid))
        get {
          implicit val ec: ExecutionContext = executor
          val fInstitutionDetails = institutionDetails(fid, institutionsActor, filingsActor)
          onComplete(fInstitutionDetails) {
            case Success(institution) =>
              complete(ToResponseMarshallable(institution))
            case Failure(error) =>
              log.error(error.getLocalizedMessage)
              complete(HttpResponse(StatusCodes.InternalServerError))
          }
        }
      }
    }

  val filingByPeriodPath =
    path("institutions" / Segment / "filings" / Segment) { (fid, period) =>
      extractExecutionContext { executor =>
        val filingsActor = system.actorOf(FilingPersistence.props(fid))
        val submissionActor = system.actorOf(SubmissionPersistence.props(fid, period))
        get {
          implicit val ec: ExecutionContext = executor
          val fDetails: Future[FilingDetail] = filingDetailsByPeriod(period, filingsActor, submissionActor)
          onComplete(fDetails) {
            case Success(filingDetails) =>
              filingsActor ! Shutdown
              submissionActor ! Shutdown
              complete(ToResponseMarshallable(filingDetails))
            case Failure(error) =>
              filingsActor ! Shutdown
              submissionActor ! Shutdown
              complete(HttpResponse(StatusCodes.InternalServerError))
          }
        }
      }
    }

  val submissionPath =
    path("institutions" / Segment / "filings" / Segment / "submissions") { (fid, period) =>
      val submissionsActor = system.actorOf(SubmissionPersistence.props(fid, period))
      post {
        val submissionsActor = system.actorOf(SubmissionPersistence.props(fid, period))
        submissionsActor ! CreateSubmission
        val fLatest = (submissionsActor ? GetLatestSubmission).mapTo[Submission]
        onComplete(fLatest) {
          case Success(submission) =>
            submissionsActor ! Shutdown
            val e = HttpEntity(ContentTypes.`application/json`, submission.toJson.toString)
            complete(HttpResponse(StatusCodes.Created, entity = e))
          case Failure(error) =>
            submissionsActor ! Shutdown
            complete(HttpResponse(StatusCodes.InternalServerError))
        }
      }
    }

  val institutionSummaryPath =
    path("institutions" / Segment / "summary") { fid =>
      val institutionsActor = system.actorSelection("/user/institutions")
      val filingsActor = system.actorOf(FilingPersistence.props(fid))
      implicit val ec = system.dispatcher //TODO: customize ExecutionContext
      get {
        val fInstitution = (institutionsActor ? GetInstitutionById(fid)).mapTo[Institution]
        val fFilings = (filingsActor ? GetState).mapTo[Seq[Filing]]
        val fSummary = for {
          institution <- fInstitution
          filings <- fFilings
        } yield InstitutionSummary(institution.id, institution.name, filings)

        onComplete(fSummary) {
          case Success(summary) =>
            filingsActor ! Shutdown
            complete(ToResponseMarshallable(summary))
          case Failure(error) =>
            filingsActor ! Shutdown
            complete(HttpResponse(StatusCodes.InternalServerError))
        }
      }
    }

  private def institutionDetails(fid: String, institutionsActor: ActorSelection, filingsActor: ActorRef)(implicit ec: ExecutionContext): Future[InstitutionDetail] = {
    val fInstitution = (institutionsActor ? GetInstitutionById(fid)).mapTo[Institution]
    for {
      institution <- fInstitution
      filings <- (filingsActor ? GetState).mapTo[Seq[Filing]]
    } yield InstitutionDetail(institution, filings)
  }

  private def filingDetailsByPeriod(period: String, filingsActor: ActorRef, submissionActor: ActorRef)(implicit ec: ExecutionContext): Future[FilingDetail] = {
    val fFiling = (filingsActor ? GetFilingByPeriod(period)).mapTo[Filing]
    for {
      filing <- fFiling
      submissions <- (submissionActor ? GetState).mapTo[Seq[Submission]]
    } yield FilingDetail(filing, submissions)
  }

  val institutionsRoutes =
    institutionsPath ~
      institutionByIdPath ~
      institutionSummaryPath ~
      filingByPeriodPath ~
      submissionPath
}
