from pathlib import Path
p=Path(__file__).with_name('MainActivity.kt')
s=p.read_text()
a=s.index('                AbDetachedBrowser(onFailure={message ->')
b=s.index('                                                currentBookAuthor=root.optString',a)
c=s.index('                                        }\n                                        }\n                                            },',b)
body=s[b:c]
end=s.index('                }\n            }\n        }\n\n        }\n        }\n        AbModernApp',c)
replacement='''                HiddenBookWebView(onFailure={message ->
                    if(navigationActive[0] && navigationId==loadId) {
                        status=message;resumeAfterLoad=false
                    }
                }) {web ->
                    navigationWeb[0]=web
                    AbOpenPageTask(web,currentUrl,
                        {message ->
                            if(navigationActive[0] && navigationId==loadId)status=message
                        }) {root,url,message ->
                        if(navigationActive[0] && navigationId==loadId) {
                            if(root==null) {
                                status=message
                                resumeAfterLoad=false
                            } else modernWork.launch {
                                try {
'''+body+'''                            }
                        }
                    }
                }
'''
s=s[:a]+replacement+s[end+len('                }\n'):]
s=s.replace('AbDetachedBrowser','AbParsingBrowser').replace('4.0-shake-extend-1','4.0-search-window-1')
p.write_text(s)
