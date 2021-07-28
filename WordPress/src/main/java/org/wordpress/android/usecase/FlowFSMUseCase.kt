package org.wordpress.android.usecase

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

abstract class FlowFSMUseCase<RESOURCE_PROVIDER, INIT_LOGIC_PARAMETERS, ACTION_TYPE, DATA, USE_CASE_TYPE, ERROR, STATE_KEY_TYPE> (
    val initialState: StateInterface<RESOURCE_PROVIDER, ACTION_TYPE, DATA, USE_CASE_TYPE, ERROR>,
    val defaultStateKey: STATE_KEY_TYPE,
    val resourceProvider: RESOURCE_PROVIDER
) {
    private val _flowChannel = MutableSharedFlow<UseCaseResult<USE_CASE_TYPE, ERROR, DATA>>()
    private val _internalState = ConcurrentHashMap<STATE_KEY_TYPE, StateInterface<RESOURCE_PROVIDER, ACTION_TYPE, DATA, USE_CASE_TYPE, ERROR>>()//  = initialState
    private val _mutex = Mutex()

    fun subscribe(): SharedFlow<UseCaseResult<USE_CASE_TYPE, ERROR, DATA>> {
        return _flowChannel.asSharedFlow()
    }

    //protected open suspend fun runInitLogic(parameters: INIT_LOGIC_PARAMETERS) {}



    suspend fun manageAction(action: ACTION_TYPE, key: STATE_KEY_TYPE = defaultStateKey) {
        _mutex.withLock {
            val oldState = getState(key)
            val newState = oldState.runAction(resourceProvider, action, _flowChannel)
            updateState(key, oldState, newState)

        }
    }

    interface StateInterface<RESOURCE_PROVIDER, TRANSITION_ACTION, RESULT, USE_CASE_TYPE, ERROR> {
        suspend fun runAction(
            resourceProvider: RESOURCE_PROVIDER,
            action: TRANSITION_ACTION,
            flowChannel: MutableSharedFlow<UseCaseResult<USE_CASE_TYPE, ERROR, RESULT>>
        ): StateInterface<RESOURCE_PROVIDER, TRANSITION_ACTION, RESULT, USE_CASE_TYPE, ERROR>
    }

    private fun getState(key: STATE_KEY_TYPE): StateInterface<RESOURCE_PROVIDER, ACTION_TYPE, DATA, USE_CASE_TYPE, ERROR> {
        return _internalState.putIfAbsent(key, initialState) ?: initialState
    }


    private fun updateState(
        key: STATE_KEY_TYPE,
        oldState: StateInterface<RESOURCE_PROVIDER, ACTION_TYPE, DATA, USE_CASE_TYPE, ERROR>,
        newState: StateInterface<RESOURCE_PROVIDER, ACTION_TYPE, DATA, USE_CASE_TYPE, ERROR>
    ) {
        if (/*oldState != initialState && */newState == initialState) {
            // State machine on this key completed
            _internalState.remove(key)
        } else {
            _internalState[key] = newState
        }
    }

    //companion object {
    //    private const val DEFAULT_KEY = "DEFAULT_KEY"
    //}
}
