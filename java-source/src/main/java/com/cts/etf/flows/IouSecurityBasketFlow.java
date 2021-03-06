package com.cts.etf.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.cts.etf.SecurityBasket;
import com.cts.etf.contracts.SecurityBasketContract;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.corda.confidential.SwapIdentitiesFlow;
import net.corda.core.contracts.Amount;
import net.corda.core.flows.*;
import net.corda.core.identity.AnonymousParty;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;

import java.security.PublicKey;
import java.time.Duration;
import java.util.*;

public class IouSecurityBasketFlow {
	@InitiatingFlow
	@StartableByRPC
	public static class Initiator extends EtfBaseFlow {
		private final String basketIpfsHash;
		private final Party lender;
		private final Boolean anonymous;

		private final ProgressTracker.Step INITIALISING =
				new ProgressTracker.Step("Performing initial steps.");
		private final ProgressTracker.Step BUILDING =
				new ProgressTracker.Step("Performing initial steps.");
		private final ProgressTracker.Step SIGNING =
				new ProgressTracker.Step("Signing transaction.");
		private final ProgressTracker.Step COLLECTING =
				new ProgressTracker.Step("Collecting counterparty signature.") {
					@Override
					public ProgressTracker childProgressTracker() {
						return CollectSignaturesFlow.Companion.tracker();
					}
				};
		private final ProgressTracker.Step FINALISING =
				new ProgressTracker.Step("Finalising transaction.") {
					@Override
					public ProgressTracker childProgressTracker() {
						return FinalityFlow.Companion.tracker();
					}
				};

		private final ProgressTracker progressTracker = new ProgressTracker(
				INITIALISING, BUILDING, SIGNING, COLLECTING, FINALISING
		);

		public Initiator(String basketIpfsHash,
				Party lender,
				Boolean anonymous) {
			this.lender = lender;
			this.anonymous = anonymous;
			this.basketIpfsHash = basketIpfsHash;
		}

		@Override
		public ProgressTracker getProgressTracker() {
			return progressTracker;
		}

		@Suspendable
		@Override
		public SignedTransaction call() throws FlowException {
			// Step 1. Initialisation.
			progressTracker.setCurrentStep(INITIALISING);
			final SecurityBasket securityBasket = createSecurityBasket();
			final PublicKey ourSigningKey =
					securityBasket.getBorrower().getOwningKey();

			// Step 2. Building.
			progressTracker.setCurrentStep(BUILDING);
			final List<PublicKey> requiredSigners =
					securityBasket.getParticipantKeys();

			final TransactionBuilder utx =
					new TransactionBuilder(getFirstNotary())
							.addOutputState(securityBasket,
									SecurityBasketContract.SECURITY_BASKET_CONTRACT_ID)
							.addCommand(
									new SecurityBasketContract.Commands.Iou(),
									requiredSigners)
							.setTimeWindow(getServiceHub().getClock().instant(),
									Duration.ofSeconds(30));

			// Step 3. Sign the transaction.
			progressTracker.setCurrentStep(SIGNING);
			final SignedTransaction ptx =
					getServiceHub().signInitialTransaction(utx, ourSigningKey);

			// Step 4. Get the counter-party signature.
			progressTracker.setCurrentStep(COLLECTING);
			final FlowSession lenderFlow = initiateFlow(lender);
			final SignedTransaction stx = subFlow(new CollectSignaturesFlow(
					ptx,
					ImmutableSet.of(lenderFlow),
					ImmutableList.of(ourSigningKey),
					COLLECTING.childProgressTracker())
			);

			// Step 5. Finalise the transaction.
			progressTracker.setCurrentStep(FINALISING);
			Set<Party> recordTransactions = new HashSet<>();
			recordTransactions.add(getFirstNotary());
			return subFlow(new FinalityFlow(stx, recordTransactions,
					FINALISING.childProgressTracker()));
		}

		@Suspendable
		private SecurityBasket createSecurityBasket() throws FlowException {
			if (anonymous) {
				final HashMap<Party, AnonymousParty> txKeys =
						subFlow(new SwapIdentitiesFlow(lender));

				if (txKeys.size() != 2) {
					throw new IllegalStateException(
							"Something went wrong when generating confidential identities.");
				}
				else if (!txKeys.containsKey(getOurIdentity())) {
					throw new FlowException(
							"Couldn't create our conf. identity.");
				}
				else if (!txKeys.containsKey(lender)) {
					throw new FlowException(
							"Couldn't create lender's conf. identity.");
				}

				final AnonymousParty anonymousMe = txKeys.get(getOurIdentity());
				final AnonymousParty anonymousLender = txKeys.get(lender);

				return new SecurityBasket(basketIpfsHash, anonymousLender,
						anonymousMe);
			}
			else {
				return new SecurityBasket(basketIpfsHash, lender,
						getOurIdentity());
			}
		}
	}

	@InitiatedBy(IouSecurityBasketFlow.Initiator.class)
	public static class Responder extends FlowLogic<SignedTransaction> {
		private final FlowSession otherFlow;

		public Responder(FlowSession otherFlow) {
			this.otherFlow = otherFlow;
		}

		@Suspendable
		@Override
		public SignedTransaction call() throws FlowException {
			final SignedTransaction stx =
					subFlow(new EtfBaseFlow.SignTxFlowNoChecking(otherFlow,
							SignTransactionFlow.Companion.tracker()));
			return waitForLedgerCommit(stx.getId());
		}
	}
}
