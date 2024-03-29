package com.glebalekseevjk.yasmrhomework.ui.fragment

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.glebalekseevjk.yasmrhomework.R
import com.glebalekseevjk.yasmrhomework.databinding.FragmentTodoBinding
import com.glebalekseevjk.yasmrhomework.domain.entity.ResultStatus
import com.glebalekseevjk.yasmrhomework.ui.viewmodel.TodoViewModel
import com.glebalekseevjk.yasmrhomework.utils.CustomOnClickListener
import com.glebalekseevjk.yasmrhomework.utils.appComponent
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
import javax.inject.Inject

class TodoFragment : Fragment() {
    private var _binding: FragmentTodoBinding? = null
    private val binding: FragmentTodoBinding
        get() = _binding ?: throw RuntimeException("FragmentTodoBinding is null")

    private val args: TodoFragmentArgs by navArgs()

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var todoViewModel: TodoViewModel

    private lateinit var navController: NavController
    private lateinit var datePickerDialog: DatePickerDialog

    override fun onAttach(context: Context) {
        super.onAttach(context)
        context.appComponent.createTodoFragmentSubComponent().inject(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        todoViewModel = ViewModelProvider(this, viewModelFactory)[TodoViewModel::class.java]
        if (savedInstanceState == null) {
            parseParams()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = DataBindingUtil.inflate(inflater, R.layout.fragment_todo, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.todoViewModel = todoViewModel
        binding.lifecycleOwner = viewLifecycleOwner
        initNavigationUI()
        initDatePicker()
        initListeners()
        setupToolbar()
        if (todoViewModel.currentState.screenMode == TodoViewModel.MODE_ADD){
            openKeyBoardForInput()
        }
    }

    private fun openKeyBoardForInput(){
        binding.messageEt.requestFocus()
        val inputMethodManager: InputMethodManager? =
            ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
        inputMethodManager?.showSoftInput(binding.messageEt, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun initDatePicker() {
        datePickerDialog = DatePickerDialog(requireActivity())
        datePickerDialog.setButton(
            DatePickerDialog.BUTTON_POSITIVE,
            getString(R.string.ok_text),
            datePickerDialog
        )
        datePickerDialog.setOnDateSetListener { datePicker, _, _, _ ->
            todoViewModel.updateState {
                it.copy(
                    todoItem = it.todoItem.copy(
                        deadline = GregorianCalendar(
                            datePicker.year,
                            datePicker.month,
                            datePicker.dayOfMonth
                        ).timeInMillis
                    )
                )
            }
        }
    }

    private fun setupToolbar() {
        val toolbar = binding.toolbar
        toolbar.setNavigationOnClickListener {
            activity?.onBackPressed()
        }
        toolbar.inflateMenu(R.menu.todo_fragment_menu)
        toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.save_todo -> {
                    if (todoViewModel.currentState.screenMode == TodoViewModel.MODE_EDIT) {
                        todoViewModel.editTodo(todoViewModel.currentState.todoItem) { result ->
                            when (result.status) {
                                ResultStatus.SUCCESS -> {
                                }
                                ResultStatus.LOADING -> {
                                }
                                ResultStatus.FAILURE -> {
                                    todoViewModel.setupOneTimeCheckSynchronize()
                                    Toast.makeText(context, result.message, Toast.LENGTH_SHORT)
                                        .show()
                                }
                                ResultStatus.UNAUTHORIZED -> {
                                    checkAuth()
                                    Toast.makeText(context, result.message, Toast.LENGTH_SHORT)
                                        .show()
                                }
                            }
                        }
                    } else if (todoViewModel.currentState.screenMode == TodoViewModel.MODE_ADD) {
                        todoViewModel.addTodo(todoViewModel.currentState.todoItem) { result ->
                            when (result.status) {
                                ResultStatus.SUCCESS -> {
                                }
                                ResultStatus.LOADING -> {
                                }
                                ResultStatus.FAILURE -> {
                                    todoViewModel.setupOneTimeCheckSynchronize()
                                    Toast.makeText(context, result.message, Toast.LENGTH_SHORT)
                                        .show()
                                }
                                ResultStatus.UNAUTHORIZED -> {
                                    checkAuth()
                                    Toast.makeText(context, result.message, Toast.LENGTH_SHORT)
                                        .show()
                                }
                            }
                        }
                    }
                    activity?.onBackPressed()
                    true
                }
                else -> false
            }
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun parseParams() {
        if (args.todoId != NONE){
            todoViewModel.setCurrentTodoItemById(args.todoId)
            todoViewModel.setCurrentScreenMode(TodoViewModel.MODE_EDIT)
        } else {
            todoViewModel.setCurrentScreenMode(TodoViewModel.MODE_ADD)
        }
    }

    private fun initNavigationUI() {
        navController = findNavController()
    }

    private fun initListeners() {
        binding.removeClickListener = View.OnClickListener {
            if (todoViewModel.currentState.screenMode == TodoViewModel.MODE_EDIT) {
                todoViewModel.deleteTodo(todoViewModel.currentState.todoItem, {
                    val snackBar: Snackbar =
                        Snackbar.make(
                            requireActivity().findViewById(R.id.task_list_rv),
                            "Удаление через 3..",
                            3000
                        )
                    var status = false
                    val mutex = Mutex(locked = true)
                    snackBar.setAction("Отменить") {
                        status = true
                        if (mutex.isLocked) {
                            mutex.unlock()
                        }
                    }
                    snackBar.show()
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(1000)
                        snackBar.setText("Удаление через 2..")
                        delay(1000)
                        snackBar.setText("Удаление через 1..")
                        delay(1000)
                        if (mutex.isLocked) {
                            mutex.unlock()
                        }
                    }
                    mutex.withLock {
                        status
                    }
                }) { result ->
                    when (result.status) {
                        ResultStatus.SUCCESS -> {
                        }
                        ResultStatus.LOADING -> {
                        }
                        ResultStatus.FAILURE -> {
                            todoViewModel.setupOneTimeCheckSynchronize()
                            Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                        }
                        ResultStatus.UNAUTHORIZED -> {
                            checkAuth()
                            Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                activity?.onBackPressed()
            }
        }
        binding.deadlineDateClickListener = View.OnClickListener {
            datePickerDialog.show()
        }
        binding.openDatePickerClickListener = object : CustomOnClickListener {
            override fun invoke() {
                datePickerDialog.show()
            }
        }
    }

    private fun checkAuth() {
        if (!todoViewModel.currentState.isAuth) {
            navController.navigate(R.id.action_todoListFragment_to_authFragment)
        }
    }

    companion object {
        const val NONE = -1L
    }
}