package frontlinesms2.subscription

import frontlinesms2.*

class PageMessageSubscription extends frontlinesms2.page.PageMessageActivity {
	static url = 'message/subscription'
	static content = {
		keyword {}
		joinAliases {}
		leaveAliases {}
		joinAutoreplyText {}
		leaveAutoreplyText {}
		group {}
		groupCount {}
		groupLink {}
		toggleStatus {}
	}
}