<div id="tabs-1">
	<h2 class="bold" for="messageText"><g:message code="announcement.message.title" /></h2><br />
	<g:textArea name="messageText" value="${messageText}" rows="5" cols="40"/>
	<span id="send-message-stats" class="character-count"><g:message code="common.message.count" /></span> 
</div>
<g:javascript>
	$("#messageText").live("keyup", updateCount);
</g:javascript>