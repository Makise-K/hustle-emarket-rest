package org.emarket.hustle.emarkethustle.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import javax.transaction.Transactional;

import org.emarket.hustle.emarkethustle.algorithms.RiderSelection;
import org.emarket.hustle.emarkethustle.dao.BasketRepository;
import org.emarket.hustle.emarkethustle.dao.TransactionRepository;
import org.emarket.hustle.emarkethustle.entity.Basket;
import org.emarket.hustle.emarkethustle.entity.History;
import org.emarket.hustle.emarkethustle.entity.Rider;
import org.emarket.hustle.emarkethustle.entity.Transaction;
import org.emarket.hustle.emarkethustle.entity.request.GetRequestTransaction;
import org.emarket.hustle.emarkethustle.response.FailedException;
import org.emarket.hustle.emarkethustle.response.NotFoundException;
import org.emarket.hustle.emarkethustle.response.NotificationMessages;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class TransactionServiceImpl implements TransactionService
{
	RiderSelection riderSelection = RiderSelection.getInstance();

	Logger log = Logger.getLogger(TransactionServiceImpl.class.getName());
	@Autowired
	TransactionRepository transactionRepository;

	@Autowired
	BasketRepository basketRepository;

	@Autowired
	ItemService itemService;

	@Autowired
	RiderService riderService;

	@Autowired
	NotificationService notificationService;

	@Override
	public List<Transaction> getTransaction()
	{
		log.info("From GetTransaction");
		List<Transaction> transactions = transactionRepository.findAll();

		if(transactions.isEmpty())
		{
			throw new NotFoundException("TRANSACTIONS");
		}

		return transactions;
	}

	@Override
	@Transactional
	public List<Transaction> getTransaction(GetRequestTransaction getRequest)
	{
		// @formatter:off
		Pageable pageable = PageRequest.of(getRequest.getPage(),
							getRequest.getSize(),
							Sort.by(Sort.Direction.DESC, getRequest.getField()));

		Slice<Transaction> slicedTransactions = null;

		System.out.println(getRequest.getUserProfile());
		if(getRequest.getUserProfile().equals("CUSTOMER"))
		{
			System.out.println("hello");
			slicedTransactions = transactionRepository.findByCustomerId(getRequest.getUserId(), pageable);
		}
		else if(getRequest.getUserProfile().equals("RIDER"))
		{
			slicedTransactions = transactionRepository.findByRiderId(getRequest.getUserId(), pageable);
		}

		List<Transaction> transactions = slicedTransactions.getContent();

		if(transactions.isEmpty())
		{
			throw new NotFoundException("TRANSACTIONS");
		}

		return transactions;
	}

	@Override
	public Transaction getTransactionById(int id)
	{
		Optional<Transaction> transaction = transactionRepository.findById(id);

		if(transaction.isEmpty())
		{
			throw new NotFoundException("ITEM WITH ID: " + id);
		}
		return transaction.get();
	}

	@Override
	@Transactional
	public void addTransaction(List<Transaction> transactions)
	{
		log.info("multiple save transaction here");
		try
		{
			List<History> histories = new ArrayList<>();
			for (Transaction transaction:transactions)
			{
				transaction.setId(0);
				histories = transaction.getHistories();

				for (History history:histories)
				{
					history.setId(0);
					history.setTransaction(transaction);
				}
			}

			transactionRepository.saveAll(transactions);

			// i think basket of customer should be delete here
			basketRepository.deleteByCustomerId(transactions.get(0).getCustomer().getId());

		}
		catch (Exception e)
		{
			e.printStackTrace();
			throw new FailedException("SAVING TRANSACTIONS");

		}

	}


	@Override
	public void addTransaction(Transaction transaction)
	{
		log.info("single transaction");
		try
		{
			for(History history:transaction.getHistories())
			{
				history.setTransaction(transaction);
				history.setId(0);
			}
			transaction.setId(0);

			transactionRepository.save(transaction);
			basketRepository.deleteByCustomerId(transaction.getCustomer().getId());
		}
		catch (Exception e)
		{
			e.printStackTrace();
			throw new FailedException("ADDING TRANSACTIONS");

		}

	}

	@Override
	public void updateTransaction(Transaction transaction)
	{
		try
		{
			transactionRepository.save(transaction);
		}
		catch (Exception e)
		{
			throw new FailedException("UPDATING TRANSACTIONS");
		}
	}

	@Override
	public void deleteTransaction(Transaction transaction)
	{
		try
		{
			transactionRepository.delete(transaction);
		}
		catch (Exception e)
		{
			throw new FailedException("DELETING TRANSACTION");
		}

	}

	@Override
	public void deleteTransactionById(int id)
	{
		try
		{
			transactionRepository.deleteById(id);
		}
		catch (Exception e)
		{
			throw new FailedException("DELETING TRANSACTION WITH ID: " + id);
		}

	}

	@Value("${service.fee}")
	private double serviceFee;

	@Value("${delivery.fee}")
	private double deliveryFee;

//	this checkout method will convert basket into histories, stick it to
//	transaction, prepare the transaction, assign the service fee
//	and delivery fee calculate the grand total and return the transaction
//	but the transaction is not yet saved.

	@Override
	public List<Transaction> checkout(List<Basket> baskets)
	{
		log.info("from checkout");
//		log.info(baskets.get(0).getStoreAddress().equals(baskets.get(1).getStoreAddress()) + "");

		int transactionCount = 0;
		int historyCount = 0;
		List<Transaction> transactions = new ArrayList<>();

		while (!baskets.isEmpty())
		{
			Transaction transaction = new Transaction();
			transaction.setId(transactionCount++);
			String transactionPlace = baskets.get(0).getStoreAddress();
			int i = 0;

			while (i < baskets.size())
			{
				Basket basket = baskets.get(i);
				if(basket.getStoreAddress().equals(transactionPlace))
				{
					History history = new History(0, transaction, basket.getStore(),
							basket.getItem(), basket.getItem().getName(),
							basket.getItem().getPrice(), basket.getQuantity());

					history.setId(historyCount++);

					transaction.addSubTotal(history.getQuantity() * history.getPrice());
					transaction.addHistory(history);

					basketRepository.save(basket);
					baskets.remove(i);
				}
				else
				{
					log.info(i+"increment");
					i++;
				}
			}
			log.info("transactions count: " + transactions.size());
			transaction.setStation(transactionPlace);
			transaction.setServiceFee(serviceFee);
			transaction.setDeliveryFee(deliveryFee);
			transaction.setGrandTotal();
			transactions.add(transaction);
		}

		log.info(transactions.size() + "");
		return transactions;

	}

	//checkTransactionComplete - the client will request this every 5 sec to update,
	//if there are buyers who declined

	@Override
	public Transaction checkTransactionComplete(int id)
	{
		log.info("hello from the checkTransactionComplete");
		Transaction transaction = getTransactionById(id);
		int declined = 0;

		for(History history:transaction.getHistories())
		{
			if(history.getStatus().equals("Pending"))
			{
				return transaction;
			}
		}
		for(History history:transaction.getHistories())
		{

			if(history.getStatus().equals("Declined"))
			{
				transaction.setSubTotal(transaction.getSubTotal() - history.getQuantity() * history.getPrice());
				declined ++;
			}
		}

		if(declined != 0)
		{
//			notification when one/more seller declines
			notificationService.addNotification(NotificationMessages.sellerDeclines(transaction));

			transaction.setGrandTotal();
			return transaction;
		}

		log.info("hello, no Pending, and declined");
		for(History history:transaction.getHistories())
		{
			history.setStatus("Preparing");

//			notification for the sellers that the transaction has continued
			notificationService.addNotification(NotificationMessages.customerContinues(history));

		}
		transaction.setStatus("Preparing");

		updateTransaction(transaction);

//		notification when transaction has been continued
		notificationService.addNotification(NotificationMessages.transactionPreparing(transaction));
		return transaction;

	}

	@Override
	public Transaction continueTransaction(Transaction transaction)
	{
		for(History history:transaction.getHistories())
		{
			if(history.getStatus().equals("Accepted"))
			{
				history.setStatus("Preparing");

//				notification that the transaction continues, sent to those sellers
//				who accepted the order
				notificationService.addNotification(NotificationMessages.customerContinues(history));
			}
		}

		updateTransaction(transaction);
		return transaction;
	}

	@Override
	public Transaction cancelTransaction(Transaction transaction)
	{
		for(History history:transaction.getHistories())
		{
			history.setStatus("Cancelled");
			notificationService.addNotification(NotificationMessages.customerCancels(history));

		}
		transaction.setStatus("Cancelled");

		return transaction;
	}


	//request for rider to pickup their order
	@Override
	public Transaction assignRider(int id)
	{
		Transaction transaction = getTransactionById(id);

		if(transaction.getOrderType().equals("Pick Up"))
		{
			throw new FailedException("Pick Up CANNOT ASSIGN RIDER");
		}

		if(riderSelection.isStationNull(transaction.getStation()))
		{
			System.out.println("it was null");
			return null;
		}

		System.out.print("before assignment: " );
		riderSelection.printRiders();

//		you need to implement notification for the rider here.
		Rider rider = riderSelection.dequeueRider(transaction.getStation());
		rider.setStatus("Occupied");
		riderService.updateRider(rider);

		transaction.setRider(rider);
		updateTransaction(transaction);
		System.out.print("after assignment: " );
		riderSelection.printRiders();
		return transaction;

	}

	@Override
	public Transaction getRiderAssignment(int id)
	{
		return transactionRepository.findByRiderIdAndStatus(id, "Preparing");
	}

	@Override
	public Transaction onDelivery(int id)
	{
		Transaction transaction = getTransactionById(id);

		transaction.setStatus("On Delivery");
		updateTransaction(transaction);
//		notification to inform customer that the order is on the way
		notificationService.addNotification(NotificationMessages.transactionDelivering(transaction));

		return transaction;
	}

	@Override
	public Transaction arrived(int id)
	{
		Transaction transaction = getTransactionById(id);

		transaction.setStatus("Arrived");
		updateTransaction(transaction);
		notificationService.addNotification(NotificationMessages.transactionArrived(transaction));
		return transaction;
	}

	@Override
	public Transaction toRate(int id)
	{
		Transaction transaction = getTransactionById(id);

		transaction.setStatus("To Rate");

//		updating the instock of the item by decrementing it with quantity
		for(History history:transaction.getHistories())
		{
			itemService.updateItemStock(
					history.getItem().getId(),
					 (- history.getQuantity()));

//			notification for the sellers that the transaction was completed
			notificationService.addNotification(NotificationMessages.sellerTransactionComplete(history));
		}

		updateTransaction(transaction);

//		notification for the customer that the transaction was completed
		notificationService.addNotification(NotificationMessages.customerTransactionComplete(transaction));
		return transaction;
	}

	@Override
	public Transaction completed(int id)
	{
		Transaction transaction = getTransactionById(id);

		transaction.setStatus("Completed");
		updateTransaction(transaction);
		return transaction;
	}






}
