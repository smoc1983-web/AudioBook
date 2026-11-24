package com.audiobookdownloader

/** Give every source a first chance before checking its second edition. */
internal object AbEditionCheckOrder {
    fun <T> interleave(items:List<T>,site:(T)->String,limit:Int):List<T> {
        val queues=items.groupBy(site).values.map{java.util.ArrayDeque(it)}
        val result=ArrayList<T>()
        val maximum=limit.coerceAtLeast(0)
        while(result.size<maximum&&queues.any{it.isNotEmpty()}){
            for(queue in queues){
                if(result.size>=maximum)break
                if(queue.isNotEmpty())result.add(queue.removeFirst())
            }
        }
        return result
    }
}
