package ee.openeid.siga.test

import java.io.FileInputStream
import java.util.Properties

import ee.openeid.siga.test.helper.TestData.SIGNER_CERT_PEM
import ee.openeid.siga.test.utils.RequestBuilder.{hashcodeContainersDataRequestWithDefault, remoteSigningRequestWithDefault}
import ee.openeid.siga.test.utils.{DigestSigner, RequestBuilder}
import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.http.Predef.{status, _}
import io.gatling.http.protocol.HttpProtocolBuilder

import scala.concurrent.duration._

class RemoteSigningLoadSimulation extends Simulation {
  private final val HC_CREATE_CONTAINER_ENDPOINT: String = "/hashcodecontainers"
  private final val HC_REMOTE_SIGNING_INIT: String = "/hashcodecontainers/${containerId}/remotesigning"
  private final val HC_REMOTE_SIGNING_FINISH: String = "/hashcodecontainers/${containerId}/remotesigning/${generatedSignatureId}"
  private final val HC_GET_SIGNATURES_LIST: String = "/hashcodecontainers/${containerId}/signatures"
  private final val HC_VALIDATE_CONTAINER_BY_ID: String = "/hashcodecontainers/${containerId}/validationreport"
  private final val HC_GET_CONTAINER: String = "/hashcodecontainers/${containerId}"
  private final val HC_DELETE_CONTAINER: String = "/hashcodecontainers/${containerId}"

  private val randomUuidFeeder = Iterator.continually(Map("serviceUuid" -> java.util.UUID.randomUUID.toString()))
  private val uuidFeeder = Array(Map("serviceUuid" -> "a7fd7728-a3ea-4975-bfab-f240a67e894f", "signingSecret" -> "746573745365637265744b6579303031"),
    Map("serviceUuid" -> "824dcfe9-5c26-4d76-829a-e6630f434746", "signingSecret" -> "746573745365637265744b6579303032"),
    Map("serviceUuid" -> "400ff9a2-b5fb-4fde-b764-9b519963f82e", "signingSecret" -> "746573745365637265744b6579303033")).circular

  private val httpProtocol: HttpProtocolBuilder = {
    val prop = new Properties()
    prop.load(new FileInputStream("src/test/resources/application-test.properties"))
    val host = prop.getProperty("siga.hostname")
    val protocol = prop.getProperty("siga.protocol")
    val port = prop.getProperty("siga.port")
    val applicationContextPath = prop.getProperty("siga.application-context-path")
    val url = protocol + "://" + host + ":" + port + applicationContextPath
    http.baseUrl(url).contentTypeHeader("application/json")
  }

  private val loadTestScenario: ScenarioBuilder = scenario("SiGa remote siging flow load test")
    .feed(uuidFeeder)
    .repeat(3) {
      pause(1, 5).
        exec(hcCreateContainer)
        .doIf("${containerId.exists()}") {
          exec(hcRemoteSigningInit)
            .doIf("${generatedSignatureId.exists()}") {
              exec(session => hcRemoteSigningFinishRequest(session))
                .doIf("${dataToSign.exists()}") {
                  exec(hcRemoteSigningFinish).
                    exec(hcGetContainer)
                }
              }
            }
        }

  def hcRemoteSigningFinishRequest(session: Session) = {
    val dataToSign = session("dataToSign").as[String]
    val digestAlgorithm = session("digestAlgorithm").as[String]
    session.set("hcRemoteSigningFinishRequest", RequestBuilder.remoteSigningSignatureValueRequest(DigestSigner.signDigest(dataToSign, digestAlgorithm)).toString())
  }

  def hcCreateContainer = {
    http("HC_CREATE_CONTAINER")
      .post(HC_CREATE_CONTAINER_ENDPOINT)
      .body(StringBody(hashcodeContainersDataRequestWithDefault().toString)).asJson
      .check(
        status.is(200),
        jsonPath("$.containerId").optional.saveAs("containerId"))
      .sign(session => new HmacSignatureCalculator(session))
  }

  def hcRemoteSigningInit = {
    http("HC_REMOTE_SIGNING_INIT")
      .post(HC_REMOTE_SIGNING_INIT)
      .body(StringBody(remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT").toString)).asJson
      .check(
        status.is(200),
        jsonPath("$.generatedSignatureId").optional.saveAs("generatedSignatureId"),
        jsonPath("$.dataToSign").optional.saveAs("dataToSign"),
        jsonPath("$.digestAlgorithm").optional.saveAs("digestAlgorithm")
      )
      .sign(session => new HmacSignatureCalculator(session))
  }

  def hcGetSignaturesList = {
    http("HC_GET_SIGNATURES_LIST")
      .get(HC_GET_SIGNATURES_LIST)
      .check(
        status.is(200),
        jsonPath("$.signatures").optional.saveAs("signatures")
      )
      .sign(session => new HmacSignatureCalculator(session))
  }

  def hcValidateContainerById = {
    http("HC_VALIDATE_CONTAINER_BY_ID")
      .get(HC_VALIDATE_CONTAINER_BY_ID)
      .check(status.is(200),
        jsonPath("$.validationConclusion").optional.saveAs("validationConclusion")
      )
      .sign(session => new HmacSignatureCalculator(session))
  }

  def hcGetContainer = {
    http("HC_GET_CONTAINER")
      .get(HC_GET_CONTAINER)
      .check(status.is(200))
      .sign(session => new HmacSignatureCalculator(session))
  }

  def hcDeleteContainer = {
    http("HC_DELETE_CONTAINER")
      .delete(HC_DELETE_CONTAINER)
      .check(
        status.is(200),
        jsonPath("$.result").optional.saveAs("hcDeleteContainerStatus"))
      .sign(session => new HmacSignatureCalculator(session))
  }

  def hcRemoteSigningFinish() = {
    http("HC_REMOTE_SIGNING_FINISH")
      .put(HC_REMOTE_SIGNING_FINISH)
      .body(StringBody("${hcRemoteSigningFinishRequest}")).asJson
      .check(
        status.is(200),
        jsonPath("$.result").optional.saveAs("hcRemoteSigningFinishStatus")
      )
      .sign(session => new HmacSignatureCalculator(session))
  }

  setUp(loadTestScenario.inject(
    rampUsersPerSec(0) to 5 during (90 seconds),
    constantUsersPerSec(5) during (5 minutes)))
    .protocols(httpProtocol)
    .assertions(
      details("HC_CREATE_CONTAINER").responseTime.mean.lt(150),
      details("HC_CREATE_CONTAINER").successfulRequests.percent.gte(99.9),
      details("HC_REMOTE_SIGNING_INIT").responseTime.mean.lt(150),
      details("HC_REMOTE_SIGNING_INIT").successfulRequests.percent.gte(99.9),
      details("HC_REMOTE_SIGNING_FINISH").responseTime.mean.lt(1000),
      details("HC_REMOTE_SIGNING_FINISH").successfulRequests.percent.gte(99.9),
      details("HC_GET_CONTAINER").responseTime.mean.lt(150),
      details("HC_GET_CONTAINER").successfulRequests.percent.gte(99.9),
    )
}
