package app.pushcraft

import android.app.Application
import app.pushcraft.data.Repository
import app.pushcraft.notify.Poster

class PushcraftApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Poster.ensureChannels(this)
        Repository.get(this)
    }
}
