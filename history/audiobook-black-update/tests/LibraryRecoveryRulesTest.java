import com.audiobookdownloader.LibraryRecoveryRules;

public final class LibraryRecoveryRulesTest {
    public static void main(String[] args) {
        int checks=0;
        for(int status:new int[]{1,2,4,16}) {
            if(!LibraryRecoveryRules.unfinished(status))throw new AssertionError("Partial job became a finished book: "+status);
            checks++;
        }
        for(int status:new int[]{0,8,-1}) {
            if(LibraryRecoveryRules.unfinished(status))throw new AssertionError("Completed/missing job hides readable old book: "+status);
            checks++;
        }
        for(String name:new String[]{"01.mp3","02.MP3","book.m4b","chapter.m4a","01.aac","01.ogg","01.opus","01.wav","01.flac"}) {
            if(!LibraryRecoveryRules.isAudio(name))throw new AssertionError(name);checks++;
        }
        for(String name:new String[]{"cover.jpg","book_metadata.json","chapter.mp3.part","chapter.mp3.tmp","note.txt","file"}) {
            if(LibraryRecoveryRules.isAudio(name))throw new AssertionError(name);checks++;
        }
        System.out.println("PASS: "+checks+" library recovery and audio-container rules");
    }
}
