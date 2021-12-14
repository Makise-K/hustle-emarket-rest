package org.emarket.hustle.emarkethustle.restcontroller;

import org.emarket.hustle.emarkethustle.entity.ImageEntity;
import org.emarket.hustle.emarkethustle.response.ProcessConfirmation;
import org.emarket.hustle.emarkethustle.service.ImageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/images")
public class ImageRestController
{
	@Autowired
	private ImageService imageService;

	@GetMapping(value = "/{entity}/{id}",
			produces = MediaType.IMAGE_JPEG_VALUE)
	public @ResponseBody byte [] getDigitalProfile(@PathVariable String entity, @PathVariable int id)
	{
		return imageService.getImage(entity, id);
	}

	@PostMapping(value = "/digital-profiles")
	public ProcessConfirmation saveDigitalProfile(@RequestBody ImageEntity imageEntity)
	{
		if(imageEntity.getEntity().equals("customers"))
		{
			imageService.saveImage(imageEntity);
		}

		return new ProcessConfirmation("COMPLETE",
				"IMAGE",
				"DIGITAL PROFILE SAVED SUCCESSFULLY");
	}

	@DeleteMapping("/{entity}/{id}")
	public ProcessConfirmation deleteDigitalProfile(@PathVariable String entity, @PathVariable int id)
	{
		imageService.deleteImage(entity, id);

		return new ProcessConfirmation("COMPLETE",
				"IMAGE",
				"DIGITAL PROFILE DELETED SUCCESSFULLY");
	}
}
