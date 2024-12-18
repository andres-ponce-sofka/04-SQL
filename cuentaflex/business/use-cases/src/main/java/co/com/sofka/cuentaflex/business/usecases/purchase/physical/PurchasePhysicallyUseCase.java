package co.com.sofka.cuentaflex.business.usecases.purchase.physical;

import co.com.sofka.cuentaflex.business.drivenports.repositories.AccountRepository;
import co.com.sofka.cuentaflex.business.models.*;
import co.com.sofka.cuentaflex.business.usecases.common.transactions.FeesValues;
import co.com.sofka.cuentaflex.business.usecases.common.transactions.TransactionDoneResponse;
import co.com.sofka.cuentaflex.business.usecases.common.transactions.TransactionErrors;
import co.com.sofka.cuentaflex.business.usecases.common.transactions.UnidirectionalTransactionRequest;
import co.com.sofka.shared.business.usecases.ResultWith;
import co.com.sofka.shared.business.usecases.UseCase;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class PurchasePhysicallyUseCase implements UseCase<UnidirectionalTransactionRequest, ResultWith<TransactionDoneResponse>> {
    private final AccountRepository accountRepository;
    private final FeesValues feesValues;

    public PurchasePhysicallyUseCase(AccountRepository accountRepository, FeesValues feesValues) {
        this.accountRepository = accountRepository;
        this.feesValues = feesValues;
    }

    @Override
    public ResultWith<TransactionDoneResponse> execute(UnidirectionalTransactionRequest request) {
        BigDecimal twoDecimalsAmount = request.getAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal fee = this.feesValues.getPurchasePhysicallyFee();

        if (twoDecimalsAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return ResultWith.failure(TransactionErrors.INVALID_AMOUNT);
        }

        Account account = accountRepository.getByIdAndCustomerId(request.getAccountId(), request.getCustomerId());

        if (account == null) {
            return ResultWith.failure(TransactionErrors.ACCOUNT_NOT_FOUND);
        }

        Transaction transaction = new Transaction(
                null,
                twoDecimalsAmount,
                fee,
                TransactionType.PHYSICAL_PURCHASE
        );

        AccountMovement movement = new AccountMovement(
                transaction,
                AccountRole.PAYROLL
        );

        account.addAccountMovement(movement);

        account.setAmount(account.getAmount().subtract(twoDecimalsAmount).subtract(fee));

        if(account.getAmount().compareTo(BigDecimal.ZERO) < 0) {
            return ResultWith.failure(TransactionErrors.INSUFFICIENT_FUNDS);
        }

        List<Account> updatedAccounts = this.accountRepository.updateMany(account);
        Transaction firstTransaction = updatedAccounts.getFirst().getTransactionHistory().getLastMovement().getTransaction();
        TransactionDoneResponse response = new TransactionDoneResponse(
                firstTransaction.getId(),
                firstTransaction.getAmount(),
                firstTransaction.getCost(),
                firstTransaction.getTimestamp()
        );

        return ResultWith.success(response);
    }
}
