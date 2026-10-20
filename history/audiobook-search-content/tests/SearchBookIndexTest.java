import com.audiobookdownloader.SearchBookIndex;
public final class SearchBookIndexTest {
    private static void require(boolean value,String note){if(!value)throw new AssertionError(note);}
    private static SearchBookIndex.Keys key(String title,String author){return SearchBookIndex.keys(title,author);}
    private static void add(SearchBookIndex x,int id,String title,String author,String site,String url){x.put(id,key(title,author),new String[]{site},new String[]{url});}
    private static int find(SearchBookIndex x,String title,String author,String site,String url){return x.find(key(title,author),new String[]{site},new String[]{url});}
    public static void main(String[] args){
        SearchBookIndex x=new SearchBookIndex();
        add(x,0,"Отель Перекрёсток. Том II","Андрей Васильев","Книгоблуд","kb/2");
        require(find(x,"Отель Перекресток. Том 2","Васильев Андрей","Bookinfo","bi/2")==0,"cross-site, roman volume, author order and е/ё");
        require(find(x,"Отель Перекресток. Том 3","Андрей Васильев","Bookinfo","bi/3")==-1,"different volumes must remain different");
        require(find(x,"Отель Перекресток. Том 2","Другой Автор","Bookinfo","bi/other")==-1,"different known authors must remain different");
        require(find(x,"Отель Перекресток. Том 2","Андрей Васильев","Книгоблуд","kb/other")==-1,"same-site editions must not be collapsed by title");
        require(find(x,"Changed metadata","Другой Автор","Книгоблуд","kb/2")==0,"same source URL is authoritative");
        require(find(x,"Андрей Васильев Отель Перекресток. Том 2","","Bookinfo","bi/no-author")==0,"author prefix in unknown-author title");
        SearchBookIndex upgraded=new SearchBookIndex();
        add(upgraded,0,"Лес","","A","a/forest");
        require(find(upgraded,"Лес","Первый Автор","B","b/forest")==0,"known author may enrich an unknown author");
        upgraded.put(0,key("Лес","Первый Автор"),new String[]{"A","B"},new String[]{"a/forest","b/forest"});
        require(find(upgraded,"Лес","Второй Автор","C","c/forest")==-1,"upgraded entry must leave no stale wildcard author index");
        require(find(upgraded,"Лес","","C","c/unknown")==0,"unknown incoming author remains compatible");
        require(key("  Ｏтель Перекресток Том ２  ","").title.contains("том 2"),"Unicode compatibility normalization");
        require(key("Название — Bookinfo","").title.equals("название"),"source suffix removal");
        System.out.println("PASS: actual Java index identity, URL, volume, author compatibility, same-site editions, author upgrade and Unicode cases");
        long started=System.nanoTime();
        SearchBookIndex many=new SearchBookIndex();
        int count=10000;
        for(int i=0;i<count;i++)add(many,i,"Книга номер "+i,"Автор "+i,"A","a/"+i);
        for(int i=0;i<count;i++)require(find(many,"Книга номер "+i,"Автор "+i,"B","b/"+i)==i,"large distinct batch "+i);
        SearchBookIndex repeated=new SearchBookIndex();
        for(int i=0;i<count;i++)add(repeated,i,"Одинаковое название","Автор "+i,"A","a/same/"+i);
        for(int i=0;i<count;i++)require(find(repeated,"Одинаковое название","Автор "+i,"B","b/same/"+i)==i,"large same-title author bucket "+i);
        System.out.printf("PASS: %,d insert/lookups on actual index, distinct titles and same-title/different-author stress, %.3f s (desktop JVM, not Android)%n",count*4,(System.nanoTime()-started)/1e9);
    }
}
