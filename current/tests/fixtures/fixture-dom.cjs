// Minimal DOM adapter for isolated parser fixtures, with independent CSS matching.
const vm=require('vm');
function parse(html) {
  const root={tag:'document',attrs:{},children:[]};
  const stack=[root];
  const tokens=/<!--[\s\S]*?-->|<(\/?)([a-z][a-z0-9-]*)([^>]*)>|([^<]+)/gi;
  let match;
  while((match=tokens.exec(html))){
    if(match[4]){stack[stack.length-1].children.push(match[4]);continue;}
    if(!match[2])continue;
    const tag=match[2].toLowerCase();
    if(match[1]){for(let i=stack.length-1;i>0;i--)if(stack[i].tag===tag){stack.length=i;break;}continue;}
    const attrs={};
    for(const a of match[3].matchAll(/([\w-]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+)))?/g))
      attrs[a[1]]=a[2]??a[3]??a[4]??'';
    const node={tag,attrs,children:[]};stack[stack.length-1].children.push(node);
    if(!['img','br','hr','input','meta','link','source'].includes(tag))stack.push(node);
  }
  return root;
}
function selectors(value) {
  const output=[];let item='',depth=0;
  for(const char of value){if(char==='[')depth++;if(char===']')depth--;if(char===','&&depth===0){output.push(item.trim());item='';}else item+=char;}
  output.push(item.trim());return output;
}
function matchesSimple(element,selector) {
  if(!element.tagName)return false;
  let value=selector;
  const attrs=[...value.matchAll(/\[([\w-]+)(?:(\*=|\^=|\$=|=)["']?([^\]"']*)["']?)?\]/g)];
  value=value.replace(/\[[^\]]+\]/g,'');
  const tag=value.match(/^[\w-]+/);
  if(tag&&element.tagName.toLowerCase()!==tag[0].toLowerCase())return false;
  for(const match of value.matchAll(/\.([\w-]+)/g))if(!element.className.split(/\s+/).includes(match[1]))return false;
  for(const match of value.matchAll(/#([\w-]+)/g))if(element.getAttribute('id')!==match[1])return false;
  for(const [,name,operator,expected]of attrs){
    const actual=element.getAttribute(name);if(actual===null)return false;
    if(operator==='='&&actual!==expected)return false;
    if(operator==='*='&&!actual.includes(expected))return false;
    if(operator==='^='&&!actual.startsWith(expected))return false;
    if(operator==='$='&&!actual.endsWith(expected))return false;
  }
  return true;
}
function matches(element,selector) {
  // Split descendant selectors only outside brackets/quoted attribute values.
  const pieces=[];let piece='',depth=0;
  for(const char of selector){if(char==='[')depth++;if(char===']')depth--;if(/\s/.test(char)&&depth===0){if(piece){pieces.push(piece);piece='';}}else piece+=char;}
  if(piece)pieces.push(piece);
  if(!matchesSimple(element,pieces.pop()))return false;
  let ancestor=element.parentElement;
  while(pieces.length){const previous=pieces.pop();while(ancestor&&!matchesSimple(ancestor,previous))ancestor=ancestor.parentElement;if(!ancestor)return false;ancestor=ancestor.parentElement;}
  return true;
}
function makeDocument(html,url){
  const location=new URL(url);let context;
  class Element{
    constructor(node,parent){this.tagName=node.tag.toUpperCase();this.attrs=node.attrs;this.parentElement=parent;this.children=node.children.map(child=>typeof child==='string'?child:new Element(child,this));this.onclick=null;}
    get textContent(){return this.children.map(child=>typeof child==='string'?child:child.textContent).join('');}
    get innerText(){return this.textContent;}
    get className(){return this.attrs.class||'';}
    get href(){return new URL(this.attrs.href||'',location.href).href;}
    get src(){return this.attrs.src?new URL(this.attrs.src,location.href).href:'';}
    get currentSrc(){return this.src;}
    get disabled(){return 'disabled'in this.attrs;}
    set disabled(value){if(value)this.attrs.disabled='';else delete this.attrs.disabled;}
    getAttribute(name){return name in this.attrs?String(this.attrs[name]??''):null;}
    querySelectorAll(selector){const parts=selectors(selector);const found=[];const visit=node=>{for(const child of node.children){if(typeof child==='string')continue;if(parts.some(part=>matches(child,part)))found.push(child);visit(child);}};visit(this);return found;}
    querySelector(selector){return this.querySelectorAll(selector)[0]||null;}
    closest(selector){let node=this;const parts=selectors(selector);while(node){if(parts.some(part=>matches(node,part)))return node;node=node.parentElement;}return null;}
    contains(other){let node=other;while(node){if(node===this)return true;node=node.parentElement;}return false;}
    insertAdjacentHTML(position,html){if(position!=='beforeend')throw new Error('unsupported position');const parsed=parse(html);for(const node of parsed.children)this.children.push(typeof node==='string'?node:new Element(node,this));}
    click(){if(this.onclick)this.onclick();else if(this.attrs.onclick)vm.runInContext(`(function(){${this.attrs.onclick}}).call(button)`,Object.assign(context,{button:this}));}
  }
  const document=new Element(parse(html),null);
  document.body=document.querySelector('body')||document;
  document.scripts=document.querySelectorAll('script');
  document.title=document.querySelector('title')?.textContent||'';
  document.getElementById=id=>document.querySelector('#'+id);
  const window={location,scrollTo(){},performance:{getEntriesByType(){return[];}}};
  context=vm.createContext({document,location,window,URL,console,performance:window.performance});
  return {evaluate(script){return vm.runInContext(script,context);}};
}
module.exports={makeDocument};
