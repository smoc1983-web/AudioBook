package com.audiobookdownloader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

internal enum class AbSupportBank(val title:String,val packageId:String) {
    TBANK("Т-Банк","com.idamob.tinkoff.android"),
    SBER("СберБанк","ru.sberbankmobile"),
    OZON("Ozon Банк","ru.ozon.fintech.finance")
}
internal object AbSupportBanks {
    fun open(context:Context,bank:AbSupportBank) {
        try {
            val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Поддержка разработчика","+79128580680"))
        } catch(_:Exception) {
            Toast.makeText(context,"Не удалось скопировать номер",Toast.LENGTH_LONG).show()
            return
        }
        try {
            val launch=context.packageManager.getLaunchIntentForPackage(bank.packageId)
            if(launch!=null) {
                context.startActivity(launch)
                Toast.makeText(context,"Номер скопирован. Выберите перевод по номеру и проверьте получателя.",Toast.LENGTH_LONG).show()
                return
            }
        } catch(_:Exception) {}
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.rustore.ru/catalog/app/"+bank.packageId)))
            Toast.makeText(context,"Номер скопирован. Открыта страница банка в RuStore.",Toast.LENGTH_LONG).show()
        } catch(_:Exception) {
            Toast.makeText(context,"Номер скопирован, но открыть банк не удалось.",Toast.LENGTH_LONG).show()
        }
    }
}
