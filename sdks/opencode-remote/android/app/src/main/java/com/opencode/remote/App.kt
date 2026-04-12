package com.opencode.remote

import android.app.Application
import com.opencode.remote.service.ConnectionService
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

// Application 入口，启动时初始化连接服务
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val ctx = this
        MainScope().launch {
            ConnectionService.init(ctx)
        }
    }
}
