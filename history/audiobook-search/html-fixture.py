import sys, json
from html.parser import HTMLParser
class Parser(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.root={'tag':'document','attrs':{},'children':[]}
        self.stack=[self.root]
    def handle_starttag(self,tag,attrs):
        node={'tag':tag,'attrs':dict(attrs),'children':[]}
        self.stack[-1]['children'].append(node)
        if tag not in {'img','br','hr','input','meta','link','source','area','base','wbr'}:self.stack.append(node)
    def handle_endtag(self,tag):
        for index in range(len(self.stack)-1,0,-1):
            if self.stack[index]['tag']==tag:
                del self.stack[index:]
                return
    def handle_data(self,data):self.stack[-1]['children'].append(data)
p=Parser();p.feed(sys.stdin.read());print(json.dumps(p.root))
