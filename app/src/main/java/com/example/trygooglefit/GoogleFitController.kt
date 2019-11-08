package com.example.trygooglefit

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.data.Bucket
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import com.google.android.gms.fitness.data.Field.FIELD_STEPS
import com.google.android.gms.fitness.request.DataReadRequest
import com.google.android.gms.tasks.Tasks
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.TimeUnit
import com.google.android.gms.fitness.request.SessionReadRequest



class GoogleFitController {

    private val TAG = this.javaClass.simpleName
    private val supervisorJob = SupervisorJob()
    private val scope = Scope(Scopes.FITNESS_ACTIVITY_READ)

    companion object {
        val INSTANCE: GoogleFitController by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { GoogleFitController() }
    }

    fun requestPermissions(activity: Activity, requestCode: Int) {
        val googleSignInClient = GoogleSignIn.getClient(activity, getGoogleSignInOptions())
        activity.startActivityForResult(googleSignInClient.signInIntent, requestCode)
    }

    fun disconnect(context: Context, onDisconnect: (() -> Unit)? = null) {
        GoogleSignIn.getClient(context, getGoogleSignInOptions())
            .revokeAccess()
            .addOnCompleteListener {
                onDisconnect?.invoke()
            }
    }

    fun hasPermissions(context: Context, onResult: ((hasPermissions: Boolean) -> Unit)?) {
        silentSignIn(context) { isSilentSignIn ->
            Log.d(TAG, "hasPermissions( isSilentSignIn ? $isSilentSignIn")
            if (isSilentSignIn) {
                val has =
                    GoogleSignIn.hasPermissions(GoogleSignIn.getLastSignedInAccount(context), scope)
                Log.d(TAG, "hasPermissions( GoogleSignIn.hasPermissions ? $has")
                onResult?.invoke(has)
            } else {
                Log.d(TAG, "hasPermissions( return false")
                onResult?.invoke(false)
            }
        }
    }

    fun requestSessionData(context: Context, onResult:(text: String) -> Unit) {
        val startTime = createStartTime()
        val endTime = createEndTime()
        Log.i(TAG, "startTime = $startTime, endTime = $endTime")

        CoroutineScope(Dispatchers.IO).launch {
            GoogleSignIn.getLastSignedInAccount(context)?.run {

                // Build a session read request
                val readRequest = SessionReadRequest.Builder()
                    .setTimeInterval(startTime.time, endTime.time, TimeUnit.MILLISECONDS)
                    .read(DataType.TYPE_ACTIVITY_SEGMENT)
                    .readSessionsFromAllApps()
                    .build()

                val responseTask = Fitness
                    .getSessionsClient(context, this)
                    .readSession(readRequest)
                    .addOnFailureListener {
                        Log.e(TAG, "requestSessionData OnFailure: ${Gson().toJson(it)}")
                    }
                    .addOnCompleteListener {
                        if (it.isSuccessful) {
                            Log.d(TAG, "requestSessionData OnComplete(isSuccessful): ${Gson().toJson(it)}")
                            it.result?.sessions?.let { sessions ->
                                onResult(Gson().toJson(sessions))

                                sessions.forEach { session ->
                                    if (session.name?.toLowerCase()?.contains("sleep") == true) {
                                        Log.i(TAG, "- session: ${Gson().toJson(session)}")

                                        val time = session.getEndTime(TimeUnit.MILLISECONDS) - session.getStartTime(TimeUnit.MILLISECONDS)
                                        val minutes = (time / 1000) / 60
                                        Log.i(TAG, " minutes = $minutes")
                                        val hour = minutes / 60
                                        val minute = minutes % 60
                                        Log.i(TAG, "睡眠時間 $hour:$minute")
                                    }
                                }
                            }

                        } else {
                            disconnect(context)
                        }
                    }
                Tasks.await(responseTask)
            }
        }
    }

    fun requestHistoryData(context: Context, onResult:(text: String) -> Unit) {
        val startTime = createStartTime()
        val endTime = createEndTime()
        Log.i(TAG, "startTime = $startTime, endTime = $endTime")

        CoroutineScope(Dispatchers.IO).launch {
            GoogleSignIn.getLastSignedInAccount(context)?.run {
                val dataReadRequest: DataReadRequest = DataReadRequest.Builder()
                    .aggregate(DataType.TYPE_STEP_COUNT_DELTA, DataType.AGGREGATE_STEP_COUNT_DELTA)
                    .bucketByTime(30, TimeUnit.MINUTES)
                    .setTimeRange(startTime.time, endTime.time, TimeUnit.MILLISECONDS)
                    .build()

                val responseTask = Fitness
                    .getHistoryClient(context, this)
                    .readData(dataReadRequest)
                    .addOnFailureListener {
                        Log.e(TAG, "requestHistoryData OnFailure: ${Gson().toJson(it)}")
                    }
                    .addOnCompleteListener {
                        if (it.isSuccessful) {
                            Log.e(TAG, "requestHistoryData OnComplete(isSuccessful): ${Gson().toJson(it)}")
                            it.result?.buckets?.run {
                                parseData(this)
                                onResult(Gson().toJson(this))
                            }
                        } else {
                            disconnect(context)
                        }
                    }
                Tasks.await(responseTask)
            }
        }
    }

    private fun silentSignIn(context: Context, onResult: (isSuccess: Boolean) -> Unit) {
        Log.d(TAG, "silentSignIn")
        GoogleSignIn.getClient(context, getGoogleSignInOptions())
            .silentSignIn()
            .addOnSuccessListener {
                Log.d(TAG, "silentSignIn OnSuccess")
                onResult(true)
            }
            .addOnFailureListener {
                Log.d(TAG, "silentSignIn OnFailure")
                onResult(false)
            }
    }

    private fun getGoogleSignInOptions(): GoogleSignInOptions {
        return GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(scope)
            .build()
    }

    private fun parseData(buckets: List<Bucket>) {
        var totalSteps = 0
        buckets.forEach { bucket ->
            Log.i(TAG, "- bucket : ${Gson().toJson(bucket)}")
            bucket.dataSets?.forEach { dataSet ->
                Log.i(TAG, "   dataSet : ${Gson().toJson(dataSet)}")
                dataSet?.dataPoints?.forEach { dataPoint ->
                    Log.i(TAG, "     dataPoint : ${Gson().toJson(dataPoint)}")

                    dataPoint?.dataType?.fields?.forEach { field ->
                        Log.i(TAG, "        field : ${Gson().toJson(field)}")
                        if (Field.FIELD_STEPS == field) {
                            val stepValue = dataPoint.getValue(field).asInt()
                            Log.d(TAG, "          stepValue : ${Gson().toJson(stepValue)}")
                            totalSteps += stepValue
                        }
                    }
                }
            }
        }
        Log.d(TAG, "          > totalSteps : $totalSteps")
    }

    private fun createEndTime(): Date {
        return Calendar.getInstance().apply {
            add(Calendar.DATE, -1)
            set(Calendar.HOUR, getActualMaximum(Calendar.HOUR))
            set(Calendar.MINUTE, getActualMaximum(Calendar.MINUTE))
            set(Calendar.SECOND, getActualMaximum(Calendar.SECOND))
            set(Calendar.AM_PM, Calendar.PM)
        }.time
    }

    private fun createStartTime(): Date {
        return Calendar.getInstance().apply {
            add(Calendar.DATE, -2)
            set(Calendar.HOUR, getActualMinimum(Calendar.HOUR))
            set(Calendar.MINUTE, getActualMinimum(Calendar.MINUTE))
            set(Calendar.SECOND, getActualMinimum(Calendar.SECOND))
            set(Calendar.AM_PM, Calendar.AM)
        }.time
    }

}