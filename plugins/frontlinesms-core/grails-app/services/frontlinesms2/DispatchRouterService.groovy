package frontlinesms2

import org.apache.camel.Exchange
import org.apache.camel.Header

/** This is a Dynamic Router */
class DispatchRouterService {
	static final String RULE_PREFIX = "fconnection-"
	def appSettingsService
	def camelContext

	int counter = -1

	/**
	 * Slip should return the list of ______ to forward to, or <code>null</code> if
	 * we've done with it.
	 */
	def slip(Exchange exchange,
			@Header(Exchange.SLIP_ENDPOINT) String previous,
			@Header('requested-fconnection-id') String requestedFconnectionId) {
		def log = { println "DispatchRouterService.slip() : $it" }
		log "ENTRY"
		log "Routing exchange $exchange with previous endpoint $previous and target fconnection $requestedFconnectionId"
		log "x.in=$exchange?.in"
		log "x.in.headers=$exchange?.in?.headers"

		if(previous) {
			// We only want to pass this message to a single endpoint, so if there
			// is a previous one set, we should exit the slip.
			log "Exchange has previous endpoint from this slip.  Returning null."
			return null
		} else if(requestedFconnectionId) {
			log "Target is set, so forwarding exchange to fconnection $requestedFconnectionId"
			return "seda:out-$requestedFconnectionId"
		} else {
			def routeId
			log "appSettingsService.['routing.rules'] is ${appSettingsService.get('routing.rules')}"
			log "appSettingsService.['routing.uselastreceiver'] is ${appSettingsService.get('routing.uselastreceiver')}"

			if(appSettingsService.get('routing.rules')) {
				def fconnectionRoutingList = appSettingsService.get('routing.rules')?.tokenize(",")?.flatten()
				
				fconnectionRoutingList = fconnectionRoutingList.collect { route ->
					if(route.contains(RULE_PREFIX))  ((route - RULE_PREFIX) as Integer)
					else route
				}
				println "fconnectionRoutingList::: $fconnectionRoutingList"
				for(route in fconnectionRoutingList) {
					if(route instanceof String && route == "uselastreceiver") {	routeId = getLastReceiverId(exchange)}
					else { routeId = getCamelRouteId(Fconnection.get(route)) }
					log "Route Id selected: $routeId"
					if(routeId) break
				}
			} else if(appSettingsService.get('routing.uselastreceiver') == 'true'){
				routeId = getLastReceiverId(exchange)
			}

			if(!routeId){ // if uselastreceiver did not set the routeId
				if(appSettingsService.get('routing.otherwise') == 'any') {
					log "## Sending to any available connection ##"
					routeId = getDispatchRouteId()
				}else{
					log "## Not sending message at all ##"
				}
			}

			if(routeId) {
				log "Sending with route: $routeId"
				def fconnectionId = (routeId =~ /.*-(\d+)$/)[0][1]
				def queueName = "seda:out-$fconnectionId"
				log "Routing to $queueName"
				return queueName
			} else {
				// TODO may want to queue for retry here, after incrementing retry-count header
				throw new RuntimeException("No outbound route available for dispatch.")
			}
		}
	}
	
	def getDispatchRouteId() {
		def allOutRoutes = camelContext.routes.findAll { it.id.startsWith('out-') }
		if(allOutRoutes.size > 0) {
			// check for internet routes and prioritise them over modems
			def filteredRouteList = allOutRoutes.findAll { it.id.contains('-internet-') }
			if(!filteredRouteList) filteredRouteList = allOutRoutes.findAll { it.id.contains('-modem-') }
			if(!filteredRouteList) filteredRouteList = allOutRoutes
			
			println "DispatchRouterService.getDispatchConnectionId() : Routes available: ${filteredRouteList*.id}"
			println "DispatchRouterService.getDispatchConnectionId() : Counter has counted up to $counter"
			return filteredRouteList[++counter % filteredRouteList.size]?.id
		}
	}

	def handleCompleted(Exchange x) {
		println "DispatchRouterService.handleCompleted() : ENTRY"
		updateDispatch(x, DispatchStatus.SENT)
		println "DispatchRouterService.handleCompleted() : EXIT"
	}

	def handleFailed(Exchange x) {
		println "DispatchRouterService.handleFailed() : ENTRY"
		updateDispatch(x, DispatchStatus.FAILED)
		println "DispatchRouterService.handleFailed() : EXIT"
	}
	
	private Dispatch updateDispatch(Exchange x, s) {
		def id = x.in.getHeader('frontlinesms.dispatch.id')
		Dispatch d
		if(x.in.body instanceof Dispatch) {
			d = x.in.body
			d.refresh()
		} else {
			d = Dispatch.get(id)
		}
		
		println "DispatchRouterService.updateDispatch() : dispatch=$d" 
		
		if(d) {
			d.status = s
			if(s == DispatchStatus.SENT) d.dateSent = new Date()

			try {
				d.save(failOnError:true, flush:true)
			} catch(Exception ex) {
				log.error("Could not save dispatch $d with message $d.message", ex)
			}
		} else log.info("No dispatch found for id: $id")
	}

	private getLastReceiverId(exchange) {
		def log = { println "DispatchRouterService.slip() : $it" }
		log "Dispatch is ${exchange.in.getBody()}"
		def d = exchange.in.getBody()
		log "dispatch to send # $d ### d.dst # $d?.dst"
		def latestReceivedMessage = Fmessage.findBySrc(d.dst, [sort: 'dateCreated', order:'desc'])
		log "## latestReceivedMessage ## is $latestReceivedMessage"
		latestReceivedMessage?.receivedOn ? getCamelRouteId(latestReceivedMessage.receivedOn) : null
	}

	private getCamelRouteId(connection) {
		if(!connection) return null
		println "## Sending message with Connection with $connection ##"
		def allOutRoutes = camelContext.routes.findAll { it.id.startsWith('out-') }
		println "allOutRoutes ## $allOutRoutes"
		println "ALL ROUTE IDS ## ${allOutRoutes*.id}"
		def routeToTake = allOutRoutes.find{ it.id.endsWith("-${connection.id}") }
		println "Chosen Route ## $routeToTake"
		routeToTake?routeToTake.id:null
	}
}
