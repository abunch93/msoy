
FB_RequireFeatures(["XFBML"]);

/** Initializes facebook API using data generated by the server. */
function FBInit (inviteData)
{
    FB.init(inviteData.apiKey, inviteData.receiverPath);
}

/** Creates a wrapper function that will call a given function after facebook is initialized. If
 *  facebook does not initialize within the given time, an alert is shown and the function will
 *  not be called. */
function FBFunction (fnWorker, timeout)
{
    fnUnavail = function () {
        alert("Sorry, Facebook is currently unavailable.");
    };

    return function () {
        if(!window.FB) {
            fnUnavail();
            return;
        }
        var args = arguments;
        var timer;
        if (timeout) {
            timer = window.setTimeout(function () {
                fnUnavail();
                timer = null;
            }, timeout);
        }

        var waitingForInit = true;
        FB.ensureInit(function () {
            if (timer !== null && waitingForInit) {
                if (timer !== undefined) {
                    window.clearTimeout(timer);
                    fnWorker.apply(null, args);
                    waitingForInit = false;
                }
            }
        });
    };
}

/** Updates the Facebook stuff after we change the document. */
function FBParseDom ()
{
    if(window.FB) {
        FB.ensureInit(function () {
            FB.XFBML.Host.parseDomTree();
        });
    }
}

/** Function to show the invite GUI using data generated by the server. */
var FBShowInvite = FBFunction(function (inviteData) {
    var elem = document.getElementById(inviteData.formElemId);
    var fbInviteTag = '<fb:serverfbml><script type="text/fbml"><fb:fbml><fb:request-form \
        action="'+ inviteData.action +'" \
        method="POST" \
        invite="true" \
        type="' + inviteData.gameName + '" \
        content="' + inviteData.message + ' \
        <fb:req-choice url=\'' + inviteData.acceptPath + '#world-game___\' \
        label=\'' + inviteData.acceptLabel + '\'>"> \
        <fb:multi-friend-selector \
        showborder="false" \
        rows="4" \
        actiontext="' + inviteData.actionText + '">\
        </fb:multi-friend-selector>\
        </fb:request-form></fb:fbml></script></fb:serverfbml>';
    elem.innerHTML = fbInviteTag;
    FBParseDom();
}, 1000);