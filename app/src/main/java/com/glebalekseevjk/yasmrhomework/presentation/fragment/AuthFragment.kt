package com.glebalekseevjk.yasmrhomework.presentation.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.widget.ImageButton
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import com.glebalekseevjk.yasmrhomework.R

class AuthFragment : Fragment() {
    private lateinit var yandexAuthIBtn: ImageButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_auth, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        initListeners()
    }

    private fun initViews(view: View) {
        with(view) {
            yandexAuthIBtn = findViewById(R.id.yandex_auth_ibtn)
        }
    }

    private fun initListeners() {
        yandexAuthIBtn.setOnClickListener {
            val uri = getString(R.string.authorize_url).toUri()
            val customTabsIntent = CustomTabsIntent.Builder().build()
            customTabsIntent.intent.setPackage("com.android.chrome")
            customTabsIntent.intent.putExtra(CustomTabsIntent.EXTRA_TITLE_VISIBILITY_STATE, CustomTabsIntent.NO_TITLE);
            customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // todo: delete cookie
            customTabsIntent.launchUrl(requireContext(), uri)
        }
    }
}