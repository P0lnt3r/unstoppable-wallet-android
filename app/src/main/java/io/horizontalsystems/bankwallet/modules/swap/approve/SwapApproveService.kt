package io.horizontalsystems.bankwallet.modules.swap.approve

import io.horizontalsystems.bankwallet.core.adapters.Erc20Adapter
import io.horizontalsystems.bankwallet.entities.Coin
import io.horizontalsystems.bankwallet.modules.guides.DataState
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import java.math.BigDecimal

class SwapApproveService(
        override val coin: Coin,
        override val amount: BigDecimal,
        private val spenderAddress: String,
        private val erc20Adapter: Erc20Adapter,
        private val feeService: IFeeService
) : ISwapApproveService {

    override val approveState = BehaviorSubject.create<SwapApproveState>()

    override val feeValues
        get() = feeService.feeValues

    private val disposables = CompositeDisposable()

    init {
        approveState.onNext(SwapApproveState.ApproveNotAllowed)

        feeService.feeValues
                .subscribeOn(Schedulers.io())
                .subscribe {
                    when (it) {
                        is DataState.Success -> {
                            approveState.onNext(SwapApproveState.ApproveAllowed)
                        }
                        is DataState.Error -> {
                            approveState.onNext(SwapApproveState.Error(it.throwable))
                        }
                    }
                }
                .let {
                    disposables.add(it)
                }
    }

    override fun approve() {
        approveState.onNext(SwapApproveState.Loading)

        erc20Adapter.approve(spenderAddress, amount, feeService.gasPrice, feeService.gasLimit)
                .subscribeOn(Schedulers.io())
                .subscribe({
                    approveState.onNext(SwapApproveState.Success)
                }, {
                    approveState.onNext(SwapApproveState.Error(it))
                })
                .let {
                    disposables.add(it)
                }
    }

}