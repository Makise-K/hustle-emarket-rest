package org.emarket.hustle.emarkethustle.controller;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.logging.Logger;

import org.emarket.hustle.emarkethustle.algorithms.DocumentConverter;
import org.emarket.hustle.emarkethustle.entity.Seller;
import org.emarket.hustle.emarkethustle.security.BcryptSecurity;
import org.emarket.hustle.emarkethustle.service.SellerService;
import org.emarket.hustle.emarkethustle.service.ValidationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequestMapping("/sellers")
public class SellerController
{
	Logger log = Logger.getLogger(SellerController.class.getName());

	@Autowired
	private SellerService sellerService;

	@Autowired
	DocumentConverter documentConverter;

	@Autowired
	private ValidationService validationService;

	@Autowired
	private BcryptSecurity bcrypt;

	@GetMapping("/signup")
	public String sellerSignup(Model model)
	{
		Seller seller = new Seller();
		MultipartFile file = null;

		model.addAttribute("file", file);
		model.addAttribute("seller", seller);
		return ("sellers/signup");
	}

	@RequestMapping(path = "/save",
			method = RequestMethod.POST,
			consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
	public String saveSeller(
			@ModelAttribute("seller") Seller seller,
			@RequestParam("file") MultipartFile file)
	{
		String fileName = file.getOriginalFilename();

		System.out.println(fileName);
		seller.setId(0);
		seller.getStore().setId(0);
		seller.getStore().setSeller(seller);
		seller.setPassword(bcrypt.encode(seller.getPassword()));
		seller.getStore().setDocuments(fileName);
		seller = sellerService.saveSeller(seller);

		String fs = FileSystems.getDefault().getSeparator();

		Path basePath = FileSystems.getDefault()
				.getPath(".", "src", "main", "resources", "documents", "sellers");

		String filePath = basePath.normalize().toAbsolutePath()
				+ fs + seller.getId();

		log.info(filePath);

		documentConverter.saveDocument(filePath, fileName, file);

		return ("redirect:/");
	}

	@GetMapping("/validation")
	@ResponseBody
	public boolean validate(@RequestParam(required = false) String username, String email)
	{

		if(username != null)
		{
			return validationService.isUsernameNotTaken(username);
		}

		else if(email != null)
		{
			log.info(email);
			return validationService.isEmailNotTaken(email);
		}

		return false;

	}

}
