package com.audiobookdownloader;

import java.net.URI;
import java.util.Set;
import org.json.JSONArray;

/** Source-bound pagination only. Search never visits an arbitrary URL from a page. */
public final class SearchPaginationPolicy {
    private final ContinuationPolicy known;
    public SearchPaginationPolicy(String sourceRegistry) {
        try { known = new ContinuationPolicy(sourceRegistry); }
        catch(org.json.JSONException bad) { throw new IllegalArgumentException("Invalid source registry",bad); }
    }
    public String canonical(String url) { return known.canonical(url); }

    public String next(String searchRoot, JSONArray candidates, Set<String> visited) {
        if(candidates==null || visited==null || searchRoot==null || searchRoot.isBlank())return "";
        for(int i=0;i<Math.min(30,candidates.length());i++){
            String candidate=candidates.optString(i,"");
            if(candidate.length()>2048 || !NetworkAddressRules.publicAddress(candidate))continue;
            try {
                URI uri=new URI(candidate);
                if(!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo()!=null ||
                    (uri.getPort()!=-1 && uri.getPort()!=443) || uri.getRawFragment()!=null)continue;
                if(!known.nextPage(searchRoot,candidate))continue;
                String key=known.canonical(candidate);
                if(!key.isBlank() && !visited.contains(key))return candidate;
            }catch(Exception ignored){/* reject invalid link */}
        }
        return "";
    }
}
