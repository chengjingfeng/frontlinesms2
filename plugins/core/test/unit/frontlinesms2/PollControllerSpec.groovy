package frontlinesms2

import spock.lang.*
import grails.plugin.spock.*

class PollControllerSpec extends ControllerSpec {
	def "create action should provide groups and contacts for recipients list"() {
		setup:
			def alice = new Contact(name: "Alice", mobile: "12345")
			def bob = new Contact(name: "Bob", mobile: "54321")
			mockDomain(Contact, [alice, bob])
			def group1 = new Group(name: "group1")
			def group2 = new Group(name: "group2")
			mockDomain(Group, [group1, group2])
			mockDomain SmartGroup
			mockDomain GroupMembership, [new GroupMembership(group:group1, contact:alice),
				new GroupMembership(group:group1, contact:bob),
				new GroupMembership(group:group2, contact:bob)]
		when:
			def resultMap = controller.create()
		then:
			resultMap['contactList']*.name == ["Alice", "Bob"]
			resultMap['groupList']["group-$group1.id"].sort() == [name:"group1",addresses:["12345", "54321"]]
			resultMap['groupList']["group-$group2.id"] == [name:"group2",addresses:["54321"]]
	}
	
	def "can unarchive a poll"() {
		given:
			registerMetaClass PollController
			registerMetaClass Fmessage
			mockDomain(Poll)
			PollController.metaClass.withActivity = { Closure c -> c.call(Poll.get(mockParams.id)) }
			Fmessage.metaClass.static.owned = {Poll p, boolean starred, boolean sent -> return null}
			PollController.metaClass.message = {LinkedHashMap m -> return m}
			def poll = new Poll(name:'thingy', archived:true)
			poll.editResponses(choiceA:'One', choiceB:'Other')
			poll.save()
			assert poll.archived
		when:
			mockParams.id = poll.id
			controller.unarchive()
		then:
			!poll.archived
			controller.redirectArgs == [controller:'archive', action:'activityList']
	}
	
}
