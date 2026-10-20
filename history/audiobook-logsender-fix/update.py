from pathlib import Path
root=Path(__file__).parent
s=Path('/workspace/audiobook-crash-fix/MainActivity.kt').read_text()
def replace(old,new,count=1):
    global s
    assert s.count(old)>=count,old[:120]
    s=s.replace(old,new,count)

# Navigation browser: retain the existing page-analysis body and cancellation
# guards, removing only the AndroidView wrapper around the parser WebView.
start=s.index('                AndroidView(',s.index('val navigationActive = remember'))
end=s.index('        AbModernApp(',start)
block=s[start:end]
prefix='''                AndroidView(
                    modifier = Modifier.fillMaxWidth().requiredHeight(760.dp).alpha(0f),
                    factory = { webContext ->

                        abCreateBrowser(webContext,{message ->'''
assert prefix in block
block=block.replace(prefix,'''                AbDetachedBrowser(onFailure={message ->''',1)
block=block.replace('''                        }) {
                            navigationWeb[0] = this''','''                }) {
                            navigationWeb[0] = this''',1)
suffix='''                        }
                    }
                )
            }
        }
'''
assert suffix in block
block=block.replace(suffix,'''                }
            }
        }
''',1)
s=s[:start]+block+s[end:]

start=s.index('@Composable\nfun HiddenBookWebView')
end=s.index('\nabstract class BookWebsiteTask',start)
s=s[:start]+(root/'hidden.txt').read_text()+'\n'+s[end:]
start=s.index('private fun abCreateBrowser(')
end=s.index('private object AbWebViews',start)
s=s[:start]+(root/'browser.txt').read_text()+'\n\n'+s[end:]
replace('''// A browser belongs to its AndroidView until Compose detaches it. Never remove
// children from Compose's ViewGroup while a lazy list is measuring or disposing.
// Android can fail to create WebView when its installed provider is unavailable
// or has incompatible classes. Record these errors and report them to the caller.
''','')
replace('// The browser has a real viewport for SPA rendering, clipped away from all visible controls.',
        '// Browser sessions keep their own viewport and are not measured by Compose.')

# The three compatibility cover components can render the same bitmap directly
# in Compose; they do not need ImageView/AndroidView either.
def replace_image(function,new):
    global s
    start=s.index('AndroidView(',s.index('fun '+function+'('))
    pos=start+len('AndroidView(')
    depth=1
    while depth:
        if s[pos]=='(':depth+=1
        elif s[pos]==')':depth-=1
        pos+=1
    s=s[:start]+new+s[pos:]
replace_image('FullPlayerCover','''Image(loaded.asImageBitmap(),contentDescription=bookTitle.ifBlank{"Обложка книги"},
                contentScale=ContentScale.Fit,modifier=Modifier.fillMaxWidth().height(360.dp))''')
replace_image('SearchBookCover','''Image(loadedBitmap.asImageBitmap(),contentDescription="Обложка книги",
                contentScale=ContentScale.Crop,modifier=Modifier.fillMaxWidth().height(115.dp))''')
replace_image('BookCover','''Box(Modifier.fillMaxWidth().height(330.dp)) {
        bitmap?.let {image ->
            Image(image.asImageBitmap(),contentDescription="Обложка книги",contentScale=ContentScale.Fit,
                modifier=Modifier.fillMaxSize())
        }
    }''')
replace('import android.widget.ImageView\n','')
replace('import androidx.compose.ui.viewinterop.AndroidView\n','')
s=s.replace('4.0-startup-recovery-1','4.0-logsender-interop-1')

# A single background book verifier is enough; it avoids allocating two
# simultaneous browsing/metadata sessions while the native category loads.
replace('take((15-checked).coerceIn(0,2))','take((15-checked).coerceIn(0,1))')
(root/'MainActivity.kt').write_text(s)
print('Updated MainActivity.kt:',len(s.splitlines()),'lines')
