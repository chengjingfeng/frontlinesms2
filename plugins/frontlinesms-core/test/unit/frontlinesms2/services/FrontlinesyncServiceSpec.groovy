package frontlinesms2.services

import spock.lang.*

import frontlinesms2.*

import org.apache.camel.CamelContext
import org.apache.camel.Exchange

@TestFor(FrontlinesyncService)
class FrontlinesyncServiceSpec extends Specification {
	def connection
	def controller
	def json
	def rendered
	def queue
	def sendMessageAndHeadersInvokationCount
	def setup() {
		rendered = null
		sendMessageAndHeadersInvokationCount = 0
		service.metaClass.sendMessageAndHeaders = { q, b, h ->
			sendMessageAndHeadersInvokationCount++
			queue = q
		}
	}

	def 'apiProcess returns 403 if secret is wrong'() {
		given:
			setupConnection('thesecret')
			setupPayload('wrongSecret')
		when:
			service.apiProcess(connection, controller)	
		then:
			rendered.status == 403
	}

	def 'apiProcess returns 403 if secret is missing'() {
		given:
			setupConnection('thesecret')
			setupPayload()
		when:
			service.apiProcess(connection, controller)	
		then:
			rendered.status == 403
	}

	def 'apiProcess does not return 403 if secret is missing but connection has empty secret'() {
		given:
			setupConnection('')
			setupPayload()
		when:
			service.apiProcess(connection, controller)	
		then:
			rendered.text == 'OK'
	}

	def 'each missed call in payload is passed to incoming-missedcalls-to-store'() {
		given:
			setupConnection('secret')
			setupPayload('secret', [
				[fromNumber: 123, callTimestamp: 1233212341],	
				[fromNumber: 234, callTimestamp: 1524621462],	
				[fromNumber: 345, callTimestamp: 1426142612],	
			])
		when:
			service.apiProcess(connection, controller)
		then:
			rendered.text == 'OK'
			sendMessageAndHeadersInvokationCount == 3
			queue == 'seda:incoming-missedcalls-to-store'
	}

	private def setupConnection(secret) {
		connection = [id:123, secret:secret]
	}

	private def setupPayload(secret=null, payload=null) {
		json = [secret: secret]
		if(payload) {
			json.payload = payload
		}
		controller = [request:[JSON: json], render: { Map it -> rendered = it }]
	}
}
