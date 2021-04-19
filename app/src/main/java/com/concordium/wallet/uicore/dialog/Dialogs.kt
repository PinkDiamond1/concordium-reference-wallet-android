package com.concordium.wallet.uicore.dialog

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.concordium.wallet.App

class Dialogs {

    interface DialogFragmentListener {
        fun onDialogResult(requestCode: Int, resultCode: Int, data: Intent)
    }

    companion object {
        val POSITIVE = 0
        val NEGATIVE = 1
        val DISMISSED = 3
    }

    //region OK
    //************************************************************

    fun showOkDialog(activity: AppCompatActivity, requestCode: Int, titleId: Int, messageId: Int, positive: Int? = null) {
        val resources = App.appContext.getResources()
        showOkDialog(
            activity,
            requestCode,
            resources.getString(titleId),
            resources.getString(messageId),
            if(positive != null) resources.getString(positive) else null
        )
    }

    fun showOkDialog(
        activity: AppCompatActivity,
        requestCode: Int,
        title: String,
        message: String,
        positive: String? = null
    ) {
        val fragment = CustomDialogFragment.createOkDialog(activity, requestCode, title, message, positive)
        fragment.show(activity.supportFragmentManager, CustomDialogFragment.TAG)
    }

    //endregion

    //region OK Cancel
    //************************************************************

    fun showOkCancelDialog(
        activity: AppCompatActivity,
        requestCode: Int,
        titleId: Int,
        messageId: Int
    ) {
        val resources = App.appContext.getResources()
        showOkCancelDialog(
            activity,
            requestCode,
            resources.getString(titleId),
            resources.getString(messageId)
        )
    }

    fun showOkCancelDialog(
        activity: AppCompatActivity,
        requestCode: Int,
        title: String,
        message: String
    ) {
        val fragment =
            CustomDialogFragment.createOkCancelDialog(activity, requestCode, title, message)
        fragment.show(activity.supportFragmentManager, CustomDialogFragment.TAG)
    }

    //endregion

    //region Yes No
    //************************************************************

    fun showYesNoDialog(
        activity: AppCompatActivity,
        requestCode: Int,
        titleId: Int,
        messageId: Int
    ) {
        val resources = App.appContext.getResources()
        showYesNoDialog(
            activity,
            requestCode,
            resources.getString(titleId),
            resources.getString(messageId)
        )
    }

    fun showYesNoDialog(
        activity: AppCompatActivity,
        requestCode: Int,
        title: String,
        message: String
    ) {
        val fragment = CustomDialogFragment.createYesNoDialog(activity, requestCode, title, message)
        fragment.show(activity.supportFragmentManager, CustomDialogFragment.TAG)
    }

    //endregion

    //region Positive Negative
    //************************************************************

    fun showPositiveNegativeDialog(
        activity: AppCompatActivity,
        requestCode: Int,
        titleId: Int,
        messageId: Int,
        positive: Int,
        negative: Int
    ) {
        val resources = App.appContext.getResources()
        showPositiveNegativeDialog(
            activity,
            requestCode,
            resources.getString(titleId),
            resources.getString(messageId),
            resources.getString(positive),
            resources.getString(negative)
        )
    }

    fun showPositiveNegativeDialog(
        activity: AppCompatActivity,
        requestCode: Int,
        title: String,
        message: String,
        positive: String,
        negative: String
    ) {
        val fragment =
            CustomDialogFragment.createPositiveNegativeDialog(
                activity,
                requestCode,
                title,
                message,
                positive,
                negative
            )
        fragment.show(activity.supportFragmentManager, CustomDialogFragment.TAG)
    }

    //endregion
}