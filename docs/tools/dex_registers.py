"""Report the register frame of a named DEX method in a built APK (no Android SDK needed)."""
import json,struct,sys,zipfile

def method_frames(data, owner, name):
    def u32(p):return struct.unpack_from('<I',data,p)[0]
    def leb(p):
        value=shift=0
        while True:
            byte=data[p];p+=1;value|=(byte&127)<<shift
            if byte<128:return value,p
            shift+=7
    strings=[]
    for i in range(u32(56)):
        p=u32(u32(60)+i*4);_,p=leb(p);end=data.index(0,p)
        strings.append(data[p:end].decode('utf-8',errors='replace'))
    types=[strings[u32(u32(68)+i*4)] for i in range(u32(64))]
    methods=[]
    for i in range(u32(88)):
        cls,proto,idx=struct.unpack_from('<HHI',data,u32(92)+i*8)
        methods.append((types[cls],strings[idx]))
    for i in range(u32(96)):
        p=u32(100)+i*32
        if types[u32(p)]!=owner:continue
        p=u32(p+24)
        if not p:continue
        counts=[]
        for _ in range(4):n,p=leb(p);counts.append(n)
        for _ in range(counts[0]+counts[1]):_,p=leb(p);_,p=leb(p)
        for count in counts[2:]:
            idx=0
            for _ in range(count):
                delta,p=leb(p);idx+=delta;_,p=leb(p);code,p=leb(p)
                if methods[idx][0]==owner and (name is None or methods[idx][1]==name) and code:
                    registers,inputs=struct.unpack_from('<HH',data,code)
                    yield {'owner':owner,'method':methods[idx][1],'registers':registers,'input_words':inputs}

if __name__=='__main__':
    rows=[]
    with zipfile.ZipFile(sys.argv[1]) as package:
        for entry in package.namelist():
            if entry.endswith('.dex'):
                rows.extend(dict(row,dex=entry) for row in method_frames(package.read(entry),'Lcom/audiobookdownloader/MainActivityKt;','AudioBookApp'))
    print(json.dumps(rows,indent=2));assert rows,'Method not found'
